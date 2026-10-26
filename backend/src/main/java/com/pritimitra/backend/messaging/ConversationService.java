package com.pritimitra.backend.messaging;

import com.pritimitra.backend.common.ApiException;
import com.pritimitra.backend.common.PublicIdCursor;
import com.pritimitra.backend.messaging.ConversationDtos.ConversationPage;
import com.pritimitra.backend.messaging.ConversationDtos.ConversationSummary;
import com.pritimitra.backend.messaging.ConversationDtos.MessagePage;
import com.pritimitra.backend.messaging.ConversationDtos.MessageSummary;
import com.pritimitra.backend.messaging.ConversationDtos.ReadState;
import com.pritimitra.backend.notifications.NotificationService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Participant-only conversations and durable text messages. Sender identity always comes from the authenticated
 * subject, never from the request. Sends lock the match row so they serialize against unmatch.
 */
@Service
public class ConversationService {

	static final int MAX_MESSAGE_CHARS = 2000;
	static final int PREVIEW_CHARS = 80;
	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 50;

	private final JdbcClient jdbc;
	private final NotificationService notifications;

	ConversationService(JdbcClient jdbc, NotificationService notifications) {
		this.jdbc = jdbc;
		this.notifications = notifications;
	}

	@Transactional(readOnly = true)
	ConversationPage list(String cognitoSub, String cursor, int size) {
		int pageSize = pageSize(size);
		UUID viewerId = activeViewerId(cognitoSub);
		UUID after = PublicIdCursor.decode(cursor);
		List<ConversationSummary> rows = jdbc.sql("""
				SELECT c.public_id AS conversation_id, m.public_id AS match_id, m.status,
				       lm.body AS last_body, lm.created_at AS last_at,
				       (SELECT COUNT(*) FROM conversation_message cm
				         WHERE cm.conversation_id = c.id AND cm.sender_id <> :viewerId
				           AND cm.seq > COALESCE((SELECT rs.last_read_seq FROM conversation_read_state rs
				                                   WHERE rs.conversation_id = c.id AND rs.user_id = :viewerId), 0)) AS unread
				FROM conversation c
				JOIN profile_match m ON m.id = c.match_id
				LEFT JOIN LATERAL (
				    SELECT body, created_at FROM conversation_message
				    WHERE conversation_id = c.id ORDER BY seq DESC LIMIT 1
				) lm ON TRUE
				WHERE (m.user_low_id = :viewerId OR m.user_high_id = :viewerId)
				  AND (CAST(:cursor AS uuid) IS NULL OR c.public_id > CAST(:cursor AS uuid))
				ORDER BY c.public_id
				LIMIT :limit
				""")
				.param("viewerId", viewerId)
				.param("cursor", after)
				.param("limit", pageSize + 1)
				.query((rs, n) -> conversationSummary(rs))
				.list();
		boolean hasMore = rows.size() > pageSize;
		List<ConversationSummary> items = hasMore ? rows.subList(0, pageSize) : rows;
		String next = hasMore ? PublicIdCursor.encode(items.get(items.size() - 1).conversationId()) : null;
		return new ConversationPage(List.copyOf(items), next);
	}

	/** Newest-first history page. The cursor is the public id of the oldest message already received. */
	@Transactional(readOnly = true)
	MessagePage messages(String cognitoSub, UUID conversationPublicId, String cursor, int size) {
		int pageSize = pageSize(size);
		UUID viewerId = activeViewerId(cognitoSub);
		UUID conversationId = participantConversationId(viewerId, conversationPublicId);
		long before = Long.MAX_VALUE;
		if (cursor != null && !cursor.isBlank()) {
			UUID messagePublicId = PublicIdCursor.decode(cursor);
			before = jdbc.sql("SELECT seq FROM conversation_message WHERE public_id = :pid AND conversation_id = :cid")
					.param("pid", messagePublicId)
					.param("cid", conversationId)
					.query(Long.class)
					.optional()
					.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR",
							"Pagination cursor is invalid"));
		}
		List<MessageSummary> rows = jdbc.sql("""
				SELECT cm.public_id, cm.client_message_id, cm.body, cm.created_at, cm.sender_id, cm.seq
				FROM conversation_message cm
				WHERE cm.conversation_id = :cid AND cm.seq < :before
				ORDER BY cm.seq DESC
				LIMIT :limit
				""")
				.param("cid", conversationId)
				.param("before", before)
				.param("limit", pageSize + 1)
				.query((rs, n) -> messageSummary(rs, viewerId))
				.list();
		boolean hasMore = rows.size() > pageSize;
		List<MessageSummary> items = hasMore ? rows.subList(0, pageSize) : rows;
		String next = hasMore ? PublicIdCursor.encode(items.get(items.size() - 1).messageId()) : null;
		return new MessagePage(List.copyOf(items), next);
	}

	/**
	 * Idempotent send. A retry with the same {@code clientMessageId} returns the stored message when the body matches
	 * and is rejected with 409 when it does not. Sends are blocked once the match is no longer ACTIVE.
	 */
	@Transactional
	MessageSummary send(String cognitoSub, UUID conversationPublicId, UUID clientMessageId, String rawBody) {
		UUID viewerId = activeViewerId(cognitoSub);
		if (clientMessageId == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE", "clientMessageId is required");
		}
		String body = validateBody(rawBody);

		ConversationLock locked = lockForSend(viewerId, conversationPublicId);

		Optional<MessageSummary> existing = findByClientId(locked.conversationId(), viewerId, clientMessageId, body);
		if (existing.isPresent()) {
			return existing.get();
		}
		if (!"ACTIVE".equals(locked.matchStatus()) || locked.blocked()) {
			throw new ApiException(HttpStatus.CONFLICT, "CONVERSATION_CLOSED",
					"This conversation is closed because the match has ended");
		}

		Optional<MessageSummary> inserted = jdbc.sql("""
				INSERT INTO conversation_message (conversation_id, sender_id, client_message_id, body)
				VALUES (:cid, :sender, :clientId, :body)
				ON CONFLICT (conversation_id, sender_id, client_message_id) DO NOTHING
				RETURNING public_id, client_message_id, body, created_at
				""")
				.param("cid", locked.conversationId())
				.param("sender", viewerId)
				.param("clientId", clientMessageId)
				.param("body", body)
				.query((rs, n) -> new MessageSummary(
						rs.getObject("public_id", UUID.class),
						rs.getObject("client_message_id", UUID.class),
						rs.getString("body"),
						true,
						rs.getTimestamp("created_at").toInstant()))
				.optional();
		if (inserted.isPresent()) {
			notifications.recordNewMessage(inserted.get().messageId());
			return inserted.get();
		}
		return findByClientId(locked.conversationId(), viewerId, clientMessageId, body)
				.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
						"Message could not be confirmed; retry the send"));
	}

	/** Advances the viewer's read cursor to the given message. The cursor never moves backwards. */
	@Transactional
	ReadState markRead(String cognitoSub, UUID conversationPublicId, UUID messagePublicId) {
		UUID viewerId = activeViewerId(cognitoSub);
		UUID conversationId = participantConversationId(viewerId, conversationPublicId);
		long seq = jdbc.sql("SELECT seq FROM conversation_message WHERE public_id = :pid AND conversation_id = :cid")
				.param("pid", messagePublicId)
				.param("cid", conversationId)
				.query(Long.class)
				.optional()
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "Message not found"));
		jdbc.sql("""
				INSERT INTO conversation_read_state (conversation_id, user_id, last_read_seq, updated_at)
				VALUES (:cid, :uid, :seq, now())
				ON CONFLICT (conversation_id, user_id) DO UPDATE
				SET last_read_seq = GREATEST(conversation_read_state.last_read_seq, EXCLUDED.last_read_seq),
				    updated_at = now()
				""")
				.param("cid", conversationId)
				.param("uid", viewerId)
				.param("seq", seq)
				.update();
		long unread = jdbc.sql("""
				SELECT COUNT(*) FROM conversation_message cm
				WHERE cm.conversation_id = :cid AND cm.sender_id <> :uid
				  AND cm.seq > COALESCE((SELECT rs.last_read_seq FROM conversation_read_state rs
				                         WHERE rs.conversation_id = :cid AND rs.user_id = :uid), 0)
				""")
				.param("cid", conversationId)
				.param("uid", viewerId)
				.query(Long.class)
				.single();
		return new ReadState(unread);
	}

	private record ConversationLock(UUID conversationId, String matchStatus, boolean blocked) {
	}

	/**
	 * Locks the match row so an unmatch cannot commit between the status check and the insert. The block check runs
	 * under the same lock, so a block that commits first is always observed.
	 */
	private ConversationLock lockForSend(UUID viewerId, UUID conversationPublicId) {
		return jdbc.sql("""
				SELECT c.id AS conversation_id, m.status,
				       EXISTS (
				           SELECT 1 FROM user_block b
				           WHERE (b.blocker_id = m.user_low_id AND b.blocked_id = m.user_high_id)
				              OR (b.blocker_id = m.user_high_id AND b.blocked_id = m.user_low_id)
				       ) AS blocked
				FROM conversation c
				JOIN profile_match m ON m.id = c.match_id
				WHERE c.public_id = :pid
				  AND (m.user_low_id = :viewerId OR m.user_high_id = :viewerId)
				FOR SHARE OF m
				""")
				.param("pid", conversationPublicId)
				.param("viewerId", viewerId)
				.query((rs, n) -> new ConversationLock(rs.getObject("conversation_id", UUID.class),
						rs.getString("status"), rs.getBoolean("blocked")))
				.optional()
				.orElseThrow(ConversationService::conversationNotFound);
	}

	private UUID participantConversationId(UUID viewerId, UUID conversationPublicId) {
		return jdbc.sql("""
				SELECT c.id FROM conversation c
				JOIN profile_match m ON m.id = c.match_id
				WHERE c.public_id = :pid AND (m.user_low_id = :viewerId OR m.user_high_id = :viewerId)
				""")
				.param("pid", conversationPublicId)
				.param("viewerId", viewerId)
				.query(UUID.class)
				.optional()
				.orElseThrow(ConversationService::conversationNotFound);
	}

	/**
	 * Returns the stored message for this sender and client id. A stored body that differs from the retry is a
	 * conflict, so a reused client id can never silently return different content.
	 */
	private Optional<MessageSummary> findByClientId(UUID conversationId, UUID senderId, UUID clientMessageId,
			String body) {
		Optional<MessageSummary> found = jdbc.sql("""
				SELECT public_id, client_message_id, body, created_at
				FROM conversation_message
				WHERE conversation_id = :cid AND sender_id = :sender AND client_message_id = :clientId
				""")
				.param("cid", conversationId)
				.param("sender", senderId)
				.param("clientId", clientMessageId)
				.query((rs, n) -> new MessageSummary(
						rs.getObject("public_id", UUID.class),
						rs.getObject("client_message_id", UUID.class),
						rs.getString("body"),
						true,
						rs.getTimestamp("created_at").toInstant()))
				.optional();
		if (found.isPresent() && !found.get().body().equals(body)) {
			throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
					"This client message id was already used for different content");
		}
		return found;
	}

	/** Trims the body and enforces non-blank and length limits. Length counts code points, matching the DB check. */
	static String validateBody(String raw) {
		if (raw == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE", "Message body is required");
		}
		String body = raw.strip();
		if (body.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE", "Message cannot be empty");
		}
		if (body.codePointCount(0, body.length()) > MAX_MESSAGE_CHARS) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "MESSAGE_TOO_LONG",
					"Message must be " + MAX_MESSAGE_CHARS + " characters or fewer");
		}
		return body;
	}

	private static int pageSize(int size) {
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_SIZE",
					"Page size must be between 1 and " + MAX_PAGE_SIZE);
		}
		return size;
	}

	/** Viewers must be ACTIVE for every conversation operation, including reads of history. */
	private UUID activeViewerId(String cognitoSub) {
		return jdbc.sql("SELECT id FROM app_user WHERE cognito_sub = :sub AND status = 'ACTIVE'")
				.param("sub", cognitoSub)
				.query(UUID.class)
				.optional()
				.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ELIGIBLE",
						"Account is not eligible for this action"));
	}

	private static ApiException conversationNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "Conversation not found");
	}

	private static ConversationSummary conversationSummary(ResultSet rs) throws SQLException {
		String body = rs.getString("last_body");
		Instant lastAt = rs.getTimestamp("last_at") == null ? null : rs.getTimestamp("last_at").toInstant();
		return new ConversationSummary(
				rs.getObject("conversation_id", UUID.class),
				rs.getObject("match_id", UUID.class),
				rs.getString("status"),
				body == null ? null : preview(body),
				lastAt,
				rs.getLong("unread"));
	}

	private static MessageSummary messageSummary(ResultSet rs, UUID viewerId) throws SQLException {
		return new MessageSummary(
				rs.getObject("public_id", UUID.class),
				rs.getObject("client_message_id", UUID.class),
				rs.getString("body"),
				viewerId.equals(rs.getObject("sender_id", UUID.class)),
				rs.getTimestamp("created_at").toInstant());
	}

	private static String preview(String body) {
		if (body.codePointCount(0, body.length()) <= PREVIEW_CHARS) {
			return body;
		}
		int end = body.offsetByCodePoints(0, PREVIEW_CHARS);
		return body.substring(0, end) + "…";
	}
}
