package com.pritimitra.backend.notifications;

import com.pritimitra.backend.common.ApiException;
import com.pritimitra.backend.common.PublicIdCursor;
import com.pritimitra.backend.notifications.NotificationDtos.NotificationPage;
import com.pritimitra.backend.notifications.NotificationDtos.NotificationSummary;
import java.sql.Types;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable in-app notifications. Rows are written inside the transaction of the event they describe, so a
 * committed match or message always has its notification and a rollback never leaves one behind. Each event has a
 * deterministic dedupe key, so retries cannot duplicate notifications. Message bodies are never copied here.
 *
 * <p>External push or email delivery is not implemented; a future delivery adapter can read these rows.
 */
@Service
public class NotificationService {

	static final int DEFAULT_PAGE_SIZE = 20;
	static final int MAX_PAGE_SIZE = 50;

	private final JdbcClient jdbc;

	public NotificationService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Notifies both participants of a newly created match. A rematch of the same match row reuses its dedupe key,
	 * so a reactivated match does not notify again.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordMatch(UUID lowUserId, UUID highUserId) {
		jdbc.sql("""
				INSERT INTO notification (recipient_id, kind, match_id, conversation_id, dedupe_key)
				SELECT r.uid, 'MATCH', m.id, c.id, 'match:' || CAST(m.id AS text)
				FROM profile_match m
				JOIN conversation c ON c.match_id = m.id
				CROSS JOIN LATERAL (VALUES (m.user_low_id), (m.user_high_id)) AS r(uid)
				WHERE m.user_low_id = :low AND m.user_high_id = :high
				ON CONFLICT (recipient_id, dedupe_key) DO NOTHING
				""")
				.param("low", lowUserId)
				.param("high", highUserId)
				.update();
	}

	/** Notifies the participant who did not send the message. Called only after a new message is persisted. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void recordNewMessage(UUID messagePublicId) {
		jdbc.sql("""
				INSERT INTO notification (recipient_id, kind, match_id, conversation_id, message_id, dedupe_key)
				SELECT CASE WHEN m.user_low_id = cm.sender_id THEN m.user_high_id ELSE m.user_low_id END,
				       'NEW_MESSAGE', m.id, c.id, cm.id, 'message:' || CAST(cm.public_id AS text)
				FROM conversation_message cm
				JOIN conversation c ON c.id = cm.conversation_id
				JOIN profile_match m ON m.id = c.match_id
				WHERE cm.public_id = :pid
				ON CONFLICT (recipient_id, dedupe_key) DO NOTHING
				""")
				.param("pid", messagePublicId)
				.update();
	}

	/** Newest-first, cursor-paginated list for the caller only, with the total unread count. */
	@Transactional(readOnly = true)
	public NotificationPage list(String cognitoSub, String cursor, int size) {
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_SIZE",
					"Page size must be between 1 and " + MAX_PAGE_SIZE);
		}
		UUID viewerId = activeViewerId(cognitoSub);
		Long afterSeq = null;
		UUID after = PublicIdCursor.decode(cursor);
		if (after != null) {
			afterSeq = jdbc.sql("SELECT seq FROM notification WHERE public_id = :pid AND recipient_id = :viewer")
					.param("pid", after)
					.param("viewer", viewerId)
					.query(Long.class)
					.optional()
					.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "Cursor is invalid"));
		}

		List<NotificationSummary> rows = jdbc.sql("""
				SELECT n.public_id, n.kind, c.public_id AS conversation_public_id, n.read_at, n.created_at
				FROM notification n
				JOIN conversation c ON c.id = n.conversation_id
				WHERE n.recipient_id = :viewer
				  AND (CAST(:afterSeq AS bigint) IS NULL OR n.seq < CAST(:afterSeq AS bigint))
				ORDER BY n.seq DESC
				LIMIT :limit
				""")
				.param("viewer", viewerId, Types.OTHER)
				.param("afterSeq", afterSeq, Types.BIGINT)
				.param("limit", size + 1)
				.query((rs, n) -> new NotificationSummary(
						rs.getObject("public_id", UUID.class),
						rs.getString("kind"),
						rs.getObject("conversation_public_id", UUID.class),
						rs.getTimestamp("read_at") != null,
						rs.getTimestamp("created_at").toInstant()))
				.list();

		long unread = jdbc.sql("SELECT COUNT(*) FROM notification WHERE recipient_id = :viewer AND read_at IS NULL")
				.param("viewer", viewerId)
				.query(Long.class)
				.single();

		if (rows.size() <= size) {
			return new NotificationPage(rows, null, unread);
		}
		List<NotificationSummary> page = rows.subList(0, size);
		return new NotificationPage(page, PublicIdCursor.encode(page.get(page.size() - 1).notificationId()), unread);
	}

	/** Marks one of the caller's notifications read. Repeats keep the original read time. */
	@Transactional
	public void markRead(String cognitoSub, UUID notificationPublicId) {
		UUID viewerId = activeViewerId(cognitoSub);
		int updated = jdbc.sql("""
				UPDATE notification SET read_at = COALESCE(read_at, now())
				WHERE public_id = :pid AND recipient_id = :viewer
				""")
				.param("pid", notificationPublicId)
				.param("viewer", viewerId)
				.update();
		if (updated == 0) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notification not found");
		}
	}

	@Transactional
	public void markAllRead(String cognitoSub) {
		UUID viewerId = activeViewerId(cognitoSub);
		jdbc.sql("UPDATE notification SET read_at = now() WHERE recipient_id = :viewer AND read_at IS NULL")
				.param("viewer", viewerId)
				.update();
	}

	private UUID activeViewerId(String cognitoSub) {
		return jdbc.sql("SELECT id FROM app_user WHERE cognito_sub = :sub AND status = 'ACTIVE'")
				.param("sub", cognitoSub)
				.query(UUID.class)
				.optional()
				.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ELIGIBLE",
						"Notifications are available only to active accounts"));
	}
}
