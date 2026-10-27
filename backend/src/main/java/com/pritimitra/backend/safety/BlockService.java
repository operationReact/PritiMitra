package com.pritimitra.backend.safety;

import com.pritimitra.backend.common.ApiException;
import java.sql.Types;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Directional, idempotent blocks. Blocking also removes likes in both directions and ends any active match, so the
 * pair cannot continue through an existing conversation. Unblocking does not restore a match; a new match needs
 * fresh mutual likes.
 *
 * <p>Unknown targets return the same 204 as known ones, so the endpoint cannot be used to confirm that an account
 * exists.
 */
@Service
public class BlockService {

	private final JdbcClient jdbc;

	public BlockService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional
	public void block(String cognitoSub, UUID targetProfilePublicId) {
		UUID viewerId = activeViewerId(cognitoSub);
		Optional<UUID> targetId = targetUserId(targetProfilePublicId);
		if (targetId.isEmpty()) {
			return;
		}
		UUID target = targetId.get();
		if (target.equals(viewerId)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_BLOCK_SELF", "You cannot block yourself");
		}
		lockUsers(viewerId, target);
		// Serialize the safety transition with in-flight sends before making the block visible or closing the match.
		// Message sends lock conversation then match; they do not acquire the account locks held here, so this order
		// cannot form a lock cycle with a send. A send that already owns the conversation lock completes before this
		// block, and no send can persist after the block transaction completes.
		lockConversation(viewerId, target);
		jdbc.sql("""
				INSERT INTO user_block (blocker_id, blocked_id) VALUES (:blocker, :blocked)
				ON CONFLICT (blocker_id, blocked_id) DO NOTHING
				""")
				.param("blocker", viewerId)
				.param("blocked", target)
				.update();
		jdbc.sql("""
				DELETE FROM profile_like
				WHERE (from_user_id = :a AND to_user_id = :b) OR (from_user_id = :b AND to_user_id = :a)
				""")
				.param("a", viewerId)
				.param("b", target)
				.update();
		jdbc.sql("""
				UPDATE profile_match SET status = 'UNMATCHED', ended_at = now(), updated_at = now()
				WHERE status = 'ACTIVE'
				  AND ((user_low_id = :a AND user_high_id = :b) OR (user_low_id = :b AND user_high_id = :a))
				""")
				.param("a", viewerId)
				.param("b", target)
				.update();
	}

	@Transactional
	public void unblock(String cognitoSub, UUID targetProfilePublicId) {
		UUID viewerId = activeViewerId(cognitoSub);
		jdbc.sql("""
				DELETE FROM user_block
				WHERE blocker_id = :viewer
				  AND blocked_id = (SELECT user_id FROM onboarding_profile WHERE public_id = :pid)
				""")
				.param("viewer", viewerId, Types.OTHER)
				.param("pid", targetProfilePublicId, Types.OTHER)
				.update();
	}

	/**
	 * Locks the pair's conversation before the block row or match status is changed. This uses the same
	 * conversation-before-match ordering as message sends, so the block decision is linearized with sends.
	 * Pairs without a match/conversation have nothing to lock; the block still prevents a future match.
	 */
	private void lockConversation(UUID a, UUID b) {
		jdbc.sql("""
				SELECT c.id
				FROM conversation c
				JOIN profile_match m ON m.id = c.match_id
				WHERE (m.user_low_id = :a AND m.user_high_id = :b)
				   OR (m.user_low_id = :b AND m.user_high_id = :a)
				FOR UPDATE OF c
				""")
				.param("a", a)
				.param("b", b)
				.query(UUID.class)
				.list();
	}

	/** Locks both account rows in ascending id order, matching the lock order used by likes and matching. */
	private void lockUsers(UUID a, UUID b) {
		jdbc.sql("SELECT id FROM app_user WHERE id IN (:a, :b) ORDER BY id FOR NO KEY UPDATE")
				.param("a", a)
				.param("b", b)
				.query(UUID.class)
				.list();
	}

	private Optional<UUID> targetUserId(UUID profilePublicId) {
		return jdbc.sql("SELECT user_id FROM onboarding_profile WHERE public_id = :pid")
				.param("pid", profilePublicId)
				.query(UUID.class)
				.optional();
	}

	private UUID activeViewerId(String cognitoSub) {
		return jdbc.sql("SELECT id FROM app_user WHERE cognito_sub = :sub AND status = 'ACTIVE'")
				.param("sub", cognitoSub)
				.query(UUID.class)
				.optional()
				.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ELIGIBLE",
						"Blocking is available only to active accounts"));
	}
}
