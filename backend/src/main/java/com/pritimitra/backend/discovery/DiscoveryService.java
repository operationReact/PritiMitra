package com.pritimitra.backend.discovery;

import com.pritimitra.backend.common.ApiException;
import com.pritimitra.backend.common.PublicIdCursor;
import com.pritimitra.backend.discovery.DiscoveryDtos.DiscoveryCard;
import com.pritimitra.backend.discovery.DiscoveryDtos.DiscoveryPage;
import com.pritimitra.backend.onboarding.ConnectionGoal;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Discovery candidates are eligible, fully onboarded, ACTIVE accounts whose owners opted in to visibility.
 * Pagination is keyset-based on the opaque public identifier, so cursors never expose internal account ids.
 */
@Service
class DiscoveryService {

	static final int DEFAULT_PAGE_SIZE = 20;
	static final int MAX_PAGE_SIZE = 50;

	private static final String VIEWER_SQL = """
			SELECT u.id, u.status, COALESCE(p.discovery_min_age, 18) AS min_age, COALESCE(p.discovery_max_age, 99) AS max_age
			FROM app_user u
			LEFT JOIN onboarding_profile p ON p.user_id = u.id
			WHERE u.cognito_sub = :sub
			""";

	private static final String CANDIDATES_SQL = """
			SELECT p.public_id, p.display_name, p.bio, p.profile_prompt,
			       CASE WHEN p.gender_identity_visible THEN p.gender_identity END AS gender_identity,
			       CASE WHEN p.pronouns_visible THEN p.pronouns END AS pronouns,
			       CASE WHEN p.sexual_orientation_visible THEN p.sexual_orientation END AS sexual_orientation,
			       ARRAY(SELECT g.goal FROM user_connection_goal g WHERE g.user_id = p.user_id ORDER BY g.goal) AS goals,
			       ARRAY(SELECT i.interest FROM user_interest i WHERE i.user_id = p.user_id ORDER BY i.interest) AS interests
			FROM onboarding_profile p
			JOIN app_user u ON u.id = p.user_id
			WHERE u.status = 'ACTIVE'
			  AND p.completed_at IS NOT NULL
			  AND p.discovery_visible
			  AND p.user_id <> :viewerId
			  AND p.date_of_birth <= CURRENT_DATE - make_interval(years => :minAge)
			  AND p.date_of_birth > CURRENT_DATE - make_interval(years => :maxAgePlusOne)
			  AND NOT EXISTS (
			      SELECT 1 FROM profile_like l WHERE l.from_user_id = :viewerId AND l.to_user_id = p.user_id)
			  AND NOT EXISTS (
			      SELECT 1 FROM user_block b
			      WHERE (b.blocker_id = :viewerId AND b.blocked_id = p.user_id)
			         OR (b.blocker_id = p.user_id AND b.blocked_id = :viewerId))
			  AND (CAST(:goal AS varchar) IS NULL OR EXISTS (
			      SELECT 1 FROM user_connection_goal g WHERE g.user_id = p.user_id AND g.goal = CAST(:goal AS varchar)))
			  AND (CAST(:cursor AS uuid) IS NULL OR p.public_id > CAST(:cursor AS uuid))
			ORDER BY p.public_id
			LIMIT :limit
			""";

	private final JdbcClient jdbc;

	DiscoveryService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	DiscoveryPage discover(String cognitoSub, String cursor, ConnectionGoal goal, int size) {
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_SIZE",
					"Page size must be between 1 and " + MAX_PAGE_SIZE);
		}
		UUID after = decodeCursor(cursor);
		Viewer viewer = viewer(cognitoSub);

		// One extra row reveals whether another page exists without a second query.
		List<DiscoveryCard> rows = jdbc.sql(CANDIDATES_SQL)
				.param("viewerId", viewer.id(), Types.OTHER)
				.param("minAge", viewer.minAge())
				.param("maxAgePlusOne", viewer.maxAge() + 1)
				.param("goal", goal == null ? null : goal.name(), Types.VARCHAR)
				.param("cursor", after, Types.OTHER)
				.param("limit", size + 1)
				.query((rs, row) -> toCard(rs))
				.list();

		if (rows.size() <= size) {
			return new DiscoveryPage(rows, null);
		}
		List<DiscoveryCard> page = rows.subList(0, size);
		return new DiscoveryPage(page, encodeCursor(page.get(page.size() - 1).publicId()));
	}

	private Viewer viewer(String cognitoSub) {
		Viewer viewer = jdbc.sql(VIEWER_SQL)
				.param("sub", cognitoSub)
				.query((rs, row) -> new Viewer(
						rs.getObject("id", UUID.class),
						rs.getString("status"),
						rs.getInt("min_age"),
						rs.getInt("max_age")))
				.optional()
				.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ELIGIBLE",
						"Discovery is available only to active accounts"));
		if (!"ACTIVE".equals(viewer.status())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ELIGIBLE",
					"Discovery is available only to active accounts");
		}
		return viewer;
	}

	private static DiscoveryCard toCard(ResultSet rs) throws SQLException {
		return new DiscoveryCard(
				rs.getObject("public_id", UUID.class),
				rs.getString("display_name"),
				rs.getString("bio"),
				rs.getString("profile_prompt"),
				rs.getString("gender_identity"),
				rs.getString("pronouns"),
				rs.getString("sexual_orientation"),
				stringList(rs, "goals"),
				stringList(rs, "interests"));
	}

	private static List<String> stringList(ResultSet rs, String column) throws SQLException {
		Array array = rs.getArray(column);
		return array == null ? List.of() : List.of((String[]) array.getArray());
	}

	public static UUID decodeCursor(String cursor) {
		return PublicIdCursor.decode(cursor);
	}

	public static String encodeCursor(UUID publicId) {
		return PublicIdCursor.encode(publicId);
	}

	private record Viewer(UUID id, String status, int minAge, int maxAge) {
	}
}
