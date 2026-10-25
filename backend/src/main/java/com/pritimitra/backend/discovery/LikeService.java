package com.pritimitra.backend.discovery;

import com.pritimitra.backend.common.ApiException;
import com.pritimitra.backend.discovery.DiscoveryDtos.LikeStatus;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Directional, idempotent likes. A like never creates a match. Targets that are not currently discoverable
 * return the same 404 as unknown identifiers, so private profiles cannot be probed.
 */
@Service
class LikeService {

	private static final String TARGET_SQL = """
			SELECT p.user_id,
			       (p.discovery_visible AND p.completed_at IS NOT NULL AND u.status = 'ACTIVE') AS discoverable
			FROM onboarding_profile p
			JOIN app_user u ON u.id = p.user_id
			WHERE p.public_id = :publicId
			""";

	private final JdbcClient jdbc;

	LikeService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional
	LikeStatus like(String cognitoSub, UUID targetPublicId) {
		UUID viewerId = activeViewerId(cognitoSub);
		Target target = target(targetPublicId);
		if (target.userId().equals(viewerId)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_LIKE_SELF", "You cannot like your own profile");
		}
		if (!target.discoverable()) {
			throw profileNotFound();
		}
		jdbc.sql("""
				INSERT INTO profile_like (from_user_id, to_user_id) VALUES (:from, :to)
				ON CONFLICT (from_user_id, to_user_id) DO NOTHING
				""")
				.param("from", viewerId)
				.param("to", target.userId())
				.update();
		return new LikeStatus(true);
	}

	@Transactional
	LikeStatus unlike(String cognitoSub, UUID targetPublicId) {
		UUID viewerId = activeViewerId(cognitoSub);
		// Removing a like is always allowed, so an owner who hid a profile can still clear their own like. Repeats are no-ops.
		jdbc.sql("""
				DELETE FROM profile_like
				WHERE from_user_id = :from
				  AND to_user_id = (SELECT user_id FROM onboarding_profile WHERE public_id = :publicId)
				""")
				.param("from", viewerId)
				.param("publicId", targetPublicId)
				.update();
		return new LikeStatus(false);
	}

	private UUID activeViewerId(String cognitoSub) {
		return jdbc.sql("SELECT id FROM app_user WHERE cognito_sub = :sub AND status = 'ACTIVE'")
				.param("sub", cognitoSub)
				.query(UUID.class)
				.optional()
				.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ELIGIBLE",
						"Likes are available only to active accounts"));
	}

	private Target target(UUID publicId) {
		return jdbc.sql(TARGET_SQL)
				.param("publicId", publicId)
				.query((rs, row) -> new Target(rs.getObject("user_id", UUID.class), rs.getBoolean("discoverable")))
				.optional()
				.orElseThrow(LikeService::profileNotFound);
	}

	private static ApiException profileNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "Profile not found");
	}

	private record Target(UUID userId, boolean discoverable) {
	}
}
