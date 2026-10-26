package com.pritimitra.backend.discovery;

import com.pritimitra.backend.common.ApiException;
import com.pritimitra.backend.discovery.MatchDtos.MatchPage;
import com.pritimitra.backend.discovery.MatchDtos.MatchState;
import com.pritimitra.backend.discovery.MatchDtos.MatchSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mutual-like matching. A match exists only while both users have liked each other and both accounts are
 * eligible. Concurrent reciprocal likes serialize on the two {@code app_user} rows, locked in ascending id order,
 * so they converge on a single {@code profile_match} row. Unmatching removes both likes, so a later match needs
 * fresh mutual intent.
 */
@Service
class MatchService {

	/** Match rows for the viewer, joined to the other participant's public-safe profile fields. */
	private static final String MATCH_SELECT = """
			SELECT m.id, m.public_id, m.status, m.created_at, m.user_low_id, m.user_high_id,
			       p.display_name,
			       (ou.status = 'ACTIVE' AND p.completed_at IS NOT NULL) AS available
			FROM profile_match m
			JOIN app_user ou ON ou.id = CASE WHEN m.user_low_id = :viewerId THEN m.user_high_id ELSE m.user_low_id END
			JOIN onboarding_profile p ON p.user_id = ou.id
			WHERE (m.user_low_id = :viewerId OR m.user_high_id = :viewerId)
			""";

	private final JdbcClient jdbc;

	MatchService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Reconciles a like that the caller has already persisted in the current transaction. Must be invoked from
	 * within that transaction so the like and any match row commit or roll back together.
	 */
	MatchOutcome reconcileLike(UUID viewerId, UUID targetUserId) {
		Pair pair = pair(viewerId, targetUserId);
		lockUsers(pair);
		Optional<MatchRow> existing = findMatch(pair);
		if (existing.isPresent() && "ACTIVE".equals(existing.get().status())) {
			return new MatchOutcome(MatchState.EXISTING, existing.get().publicId());
		}
		if (!bothEligible(pair) || !hasLike(targetUserId, viewerId)) {
			return new MatchOutcome(MatchState.NONE, null);
		}
		if (existing.isPresent()) {
			jdbc.sql("""
					UPDATE profile_match SET status = 'ACTIVE', ended_at = NULL, updated_at = now()
					WHERE id = :id
					""")
					.param("id", existing.get().id())
					.update();
			return new MatchOutcome(MatchState.CREATED, existing.get().publicId());
		}
		UUID publicId = jdbc.sql("""
				INSERT INTO profile_match (user_low_id, user_high_id)
				VALUES (:low, :high)
				RETURNING public_id
				""")
				.param("low", pair.low())
				.param("high", pair.high())
				.query(UUID.class)
				.single();
		return new MatchOutcome(MatchState.CREATED, publicId);
	}

	@Transactional(readOnly = true)
	MatchPage list(String cognitoSub, String cursor, int size) {
		if (size < 1 || size > DiscoveryService.MAX_PAGE_SIZE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_SIZE",
					"Page size must be between 1 and " + DiscoveryService.MAX_PAGE_SIZE);
		}
		UUID after = DiscoveryService.decodeCursor(cursor);
		UUID viewerId = activeViewerId(cognitoSub);
		List<MatchSummary> rows = jdbc.sql(MATCH_SELECT + """
				  AND m.status = 'ACTIVE'
				  AND (CAST(:cursor AS uuid) IS NULL OR m.public_id > CAST(:cursor AS uuid))
				ORDER BY m.public_id
				LIMIT :limit
				""")
				.param("viewerId", viewerId)
				.param("cursor", after)
				.param("limit", size + 1)
				.query((rs, row) -> summary(rs))
				.list();
		if (rows.size() <= size) {
			return new MatchPage(rows, null);
		}
		List<MatchSummary> page = rows.subList(0, size);
		return new MatchPage(page, DiscoveryService.encodeCursor(page.get(page.size() - 1).matchPublicId()));
	}

	@Transactional(readOnly = true)
	MatchSummary get(String cognitoSub, UUID matchPublicId) {
		UUID viewerId = activeViewerId(cognitoSub);
		return jdbc.sql(MATCH_SELECT + "  AND m.public_id = :publicId")
				.param("viewerId", viewerId)
				.param("publicId", matchPublicId)
				.query((rs, row) -> summary(rs))
				.optional()
				.orElseThrow(MatchService::matchNotFound);
	}

	/** Idempotently ends an active match and clears both likes. Repeats return the already-unmatched state. */
	@Transactional
	MatchSummary unmatch(String cognitoSub, UUID matchPublicId) {
		UUID viewerId = activeViewerId(cognitoSub);
		MatchRow current = participantRow(viewerId, matchPublicId);
		Pair pair = new Pair(current.low(), current.high());
		lockUsers(pair);
		jdbc.sql("""
				UPDATE profile_match SET status = 'UNMATCHED', ended_at = now(), updated_at = now()
				WHERE id = :id AND status = 'ACTIVE'
				""")
				.param("id", current.id())
				.update();
		jdbc.sql("""
				DELETE FROM profile_like
				WHERE (from_user_id = :low AND to_user_id = :high) OR (from_user_id = :high AND to_user_id = :low)
				""")
				.param("low", pair.low())
				.param("high", pair.high())
				.update();
		return get(cognitoSub, matchPublicId);
	}

	private MatchRow participantRow(UUID viewerId, UUID matchPublicId) {
		return jdbc.sql("""
				SELECT id, public_id, status, user_low_id, user_high_id FROM profile_match
				WHERE public_id = :publicId AND (user_low_id = :viewerId OR user_high_id = :viewerId)
				""")
				.param("publicId", matchPublicId)
				.param("viewerId", viewerId)
				.query((rs, row) -> matchRow(rs))
				.optional()
				.orElseThrow(MatchService::matchNotFound);
	}

	private Optional<MatchRow> findMatch(Pair pair) {
		return jdbc.sql("""
				SELECT id, public_id, status, user_low_id, user_high_id FROM profile_match
				WHERE user_low_id = :low AND user_high_id = :high
				""")
				.param("low", pair.low())
				.param("high", pair.high())
				.query((rs, row) -> matchRow(rs))
				.optional();
	}

	/** Orders the pair in PostgreSQL so the canonical key matches the database's uuid ordering. */
	private Pair pair(UUID a, UUID b) {
		return jdbc.sql("""
				SELECT LEAST(CAST(:a AS uuid), CAST(:b AS uuid)) AS low,
				       GREATEST(CAST(:a AS uuid), CAST(:b AS uuid)) AS high
				""")
				.param("a", a)
				.param("b", b)
				.query((rs, row) -> new Pair(rs.getObject("low", UUID.class), rs.getObject("high", UUID.class)))
				.single();
	}

	/**
	 * FOR NO KEY UPDATE serializes writers on the pair without conflicting with the KEY SHARE locks that foreign
	 * key checks take when like rows are inserted, which avoids a deadlock between concurrent reciprocal likes.
	 */
	private void lockUsers(Pair pair) {
		jdbc.sql("SELECT id FROM app_user WHERE id IN (:low, :high) ORDER BY id FOR NO KEY UPDATE")
				.param("low", pair.low())
				.param("high", pair.high())
				.query(UUID.class)
				.list();
	}

	private boolean bothEligible(Pair pair) {
		Integer eligible = jdbc.sql("""
				SELECT COUNT(*) FROM app_user u
				JOIN onboarding_profile p ON p.user_id = u.id
				WHERE u.id IN (:low, :high) AND u.status = 'ACTIVE' AND p.completed_at IS NOT NULL
				""")
				.param("low", pair.low())
				.param("high", pair.high())
				.query(Integer.class)
				.single();
		return eligible == 2;
	}

	private boolean hasLike(UUID from, UUID to) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM profile_like WHERE from_user_id = :from AND to_user_id = :to)")
				.param("from", from)
				.param("to", to)
				.query(Boolean.class)
				.single();
	}

	private static MatchRow matchRow(ResultSet rs) throws SQLException {
		return new MatchRow(
				rs.getObject("id", UUID.class),
				rs.getObject("public_id", UUID.class),
				rs.getString("status"),
				rs.getObject("user_low_id", UUID.class),
				rs.getObject("user_high_id", UUID.class));
	}

	private static MatchSummary summary(ResultSet rs) throws SQLException {
		boolean available = rs.getBoolean("available");
		return new MatchSummary(
				rs.getObject("public_id", UUID.class),
				rs.getString("status"),
				available ? rs.getString("display_name") : null,
				available,
				rs.getObject("created_at", OffsetDateTime.class).toInstant());
	}

	private UUID activeViewerId(String cognitoSub) {
		return jdbc.sql("SELECT id FROM app_user WHERE cognito_sub = :sub AND status = 'ACTIVE'")
				.param("sub", cognitoSub)
				.query(UUID.class)
				.optional()
				.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ELIGIBLE",
						"Matches are available only to active accounts"));
	}

	private static ApiException matchNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "MATCH_NOT_FOUND", "Match not found");
	}

	record MatchOutcome(MatchState state, UUID matchPublicId) {
	}

	private record Pair(UUID low, UUID high) {
	}

	private record MatchRow(UUID id, UUID publicId, String status, UUID low, UUID high) {
	}
}
