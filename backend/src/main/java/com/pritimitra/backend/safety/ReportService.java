package com.pritimitra.backend.safety;

import com.pritimitra.backend.common.ApiException;
import com.pritimitra.backend.safety.ReportDtos.ReportReceipt;
import com.pritimitra.backend.safety.ReportDtos.ReportRequest;
import java.sql.Types;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Private safety reports. A report is accepted only for a profile the reporter can see (discoverable or sharing a
 * match) or a message in a conversation the reporter participates in. Reports are write-only for the reporter and
 * are never returned to other users. Reporting does not block the reported person; blocking is a separate action.
 */
@Service
public class ReportService {

	private static final int MAX_DETAILS_CODE_POINTS = 500;

	private final JdbcClient jdbc;

	public ReportService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional
	public ReportReceipt report(String cognitoSub, ReportRequest request) {
		UUID viewerId = activeViewerId(cognitoSub);
		if (request == null) {
			throw invalid("request is required");
		}
		String targetType = targetType(request.targetType());
		String category = category(request.category());
		String details = details(request.details());
		if (request.targetId() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT", "targetId is required");
		}
		enforceReportRateLimit(viewerId);
		return switch (targetType) {
			case "PROFILE" -> reportProfile(viewerId, request.targetId(), category, details);
			case "MESSAGE" -> reportMessage(viewerId, request.targetId(), category, details);
			default -> reportCommunityPost(viewerId, request.targetId(), category, details);
		};
	}

	private ReportReceipt reportProfile(UUID viewerId, UUID profilePublicId, String category, String details) {
		ProfileTarget target = jdbc.sql("""
				SELECT p.user_id,
				       (p.discovery_visible AND p.completed_at IS NOT NULL AND u.status = 'ACTIVE') AS discoverable,
				       EXISTS (
				           SELECT 1 FROM profile_match m
				           WHERE (m.user_low_id = :viewer AND m.user_high_id = p.user_id)
				              OR (m.user_high_id = :viewer AND m.user_low_id = p.user_id)
				       ) AS matched
				FROM onboarding_profile p
				JOIN app_user u ON u.id = p.user_id
				WHERE p.public_id = :pid
				""")
				.param("viewer", viewerId)
				.param("pid", profilePublicId)
				.query((rs, n) -> new ProfileTarget(rs.getObject("user_id", UUID.class),
						rs.getBoolean("discoverable"), rs.getBoolean("matched")))
				.optional()
				.orElseThrow(() -> targetNotFound());
		if (target.userId().equals(viewerId)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_REPORT_SELF", "You cannot report your own profile");
		}
		if (!target.discoverable() && !target.matched()) {
			throw targetNotFound();
		}
		return insertOrExisting("""
				INSERT INTO user_report (reporter_id, reported_user_id, target_type, category, details)
				VALUES (:reporter, :reported, 'PROFILE', :category, :details)
				ON CONFLICT DO NOTHING
				RETURNING public_id
				""", viewerId, target.userId(), null, category, details, """
				SELECT public_id FROM user_report
				WHERE reporter_id = :reporter AND reported_user_id = :reported
				  AND target_type = 'PROFILE' AND status = 'PENDING'
				""");
	}

	private ReportReceipt reportMessage(UUID viewerId, UUID messagePublicId, String category, String details) {
		MessageTarget target = jdbc.sql("""
				SELECT cm.id, cm.sender_id
				FROM conversation_message cm
				JOIN conversation c ON c.id = cm.conversation_id
				JOIN profile_match m ON m.id = c.match_id
				WHERE cm.public_id = :pid
				  AND (m.user_low_id = :viewer OR m.user_high_id = :viewer)
				""")
				.param("viewer", viewerId)
				.param("pid", messagePublicId)
				.query((rs, n) -> new MessageTarget(rs.getObject("id", UUID.class),
						rs.getObject("sender_id", UUID.class)))
				.optional()
				.orElseThrow(() -> targetNotFound());
		if (target.senderId().equals(viewerId)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_REPORT_OWN_MESSAGE",
					"You cannot report your own message");
		}
		return insertOrExisting("""
				INSERT INTO user_report (reporter_id, reported_user_id, target_type, target_message_id, category, details)
				VALUES (:reporter, :reported, 'MESSAGE', :messageId, :category, :details)
				ON CONFLICT DO NOTHING
				RETURNING public_id
				""", viewerId, target.senderId(), target.messageId(), category, details, """
				SELECT public_id FROM user_report
				WHERE reporter_id = :reporter AND target_message_id = :messageId
				  AND target_type = 'MESSAGE' AND status = 'PENDING'
				""");
	}

	private ReportReceipt reportCommunityPost(UUID viewerId, UUID postPublicId, String category, String details) {
		PostTarget target = jdbc.sql("""
				SELECT p.id, p.author_id
				FROM community_post p
				JOIN community_group_member gm ON gm.group_id = p.group_id
					AND gm.user_id = :viewer AND gm.status = 'ACTIVE'
				WHERE p.public_id = :pid AND p.status = 'PUBLISHED'
				  AND p.author_id IS NOT NULL
				  AND NOT EXISTS (SELECT 1 FROM user_block b
					  WHERE (b.blocker_id = :viewer AND b.blocked_id = p.author_id)
					     OR (b.blocker_id = p.author_id AND b.blocked_id = :viewer))
				""")
				.param("viewer", viewerId).param("pid", postPublicId)
				.query((rs, n) -> new PostTarget(rs.getObject("id", UUID.class), rs.getObject("author_id", UUID.class)))
				.optional().orElseThrow(() -> targetNotFound());
		if (target.authorId().equals(viewerId)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_REPORT_OWN_POST", "You cannot report your own community post");
		}
		Optional<UUID> inserted = jdbc.sql("""
				INSERT INTO user_report (reporter_id, reported_user_id, target_type, target_post_id, category, details)
				VALUES (:reporter, :reported, 'COMMUNITY_POST', :postId, :category, :details)
				ON CONFLICT DO NOTHING RETURNING public_id
				""")
				.param("reporter", viewerId).param("reported", target.authorId())
				.param("postId", target.postId(), Types.OTHER).param("category", category)
				.param("details", details, Types.VARCHAR).query(UUID.class).optional();
		UUID reportId = inserted.orElseGet(() -> jdbc.sql("""
				SELECT public_id FROM user_report WHERE reporter_id = :reporter AND target_post_id = :postId
				  AND target_type = 'COMMUNITY_POST' AND status = 'PENDING'
				""").param("reporter", viewerId).param("postId", target.postId(), Types.OTHER).query(UUID.class).single());
		return new ReportReceipt(reportId, "PENDING");
	}

	/**
	 * Duplicate submissions return the existing open report, so retries are safe and the reporter cannot flood the
	 * review queue with copies.
	 */
	private ReportReceipt insertOrExisting(String insertSql, UUID reporter, UUID reported, UUID messageId,
			String category, String details, String existingSql) {
		Optional<UUID> inserted = jdbc.sql(insertSql)
				.param("reporter", reporter)
				.param("reported", reported)
				.param("messageId", messageId, Types.OTHER)
				.param("category", category)
				.param("details", details, Types.VARCHAR)
				.query(UUID.class)
				.optional();
		UUID reportId = inserted.orElseGet(() -> jdbc.sql(existingSql)
				.param("reporter", reporter)
				.param("reported", reported)
				.param("messageId", messageId, Types.OTHER)
				.query(UUID.class)
				.single());
		return new ReportReceipt(reportId, "PENDING");
	}


	/**
	 * Serializes submissions per reporter and limits newly persisted reports to ten per rolling hour.
	 * The user-row lock makes the count-and-insert sequence safe against parallel requests from one account.
	 */
	private void enforceReportRateLimit(UUID reporterId) {
		jdbc.sql("SELECT id FROM app_user WHERE id = :reporter FOR UPDATE")
				.param("reporter", reporterId)
				.query(UUID.class)
				.single();
		long recent = jdbc.sql("""
				SELECT count(*) FROM user_report
				WHERE reporter_id = :reporter AND created_at >= clock_timestamp() - interval '1 hour'
				""").param("reporter", reporterId).query(Long.class).single();
		if (recent >= 10) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "REPORT_RATE_LIMITED",
					"You have submitted too many reports. Try again later");
		}
	}

	private static String targetType(String raw) {
		if (raw == null) {
			throw invalid("targetType is required");
		}
		String value = raw.trim().toUpperCase(Locale.ROOT);
		if (!value.equals("PROFILE") && !value.equals("MESSAGE") && !value.equals("COMMUNITY_POST")) {
			throw invalid("targetType must be PROFILE, MESSAGE or COMMUNITY_POST");
		}
		return value;
	}

	private static String category(String raw) {
		if (raw == null) {
			throw invalid("category is required");
		}
		String value = raw.trim().toUpperCase(Locale.ROOT);
		return switch (value) {
			case "HARASSMENT", "IMPERSONATION", "UNWANTED_CONTENT", "SPAM", "OTHER" -> value;
			default -> throw invalid("category is not supported");
		};
	}

	private static String details(String raw) {
		if (raw == null) {
			return null;
		}
		String trimmed = raw.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		if (trimmed.codePointCount(0, trimmed.length()) > MAX_DETAILS_CODE_POINTS) {
			throw invalid("details must be at most " + MAX_DETAILS_CODE_POINTS + " characters");
		}
		return trimmed;
	}

	private static ApiException invalid(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT", message);
	}

	private static ApiException targetNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "REPORT_TARGET_NOT_FOUND", "Report target not found");
	}

	private UUID activeViewerId(String cognitoSub) {
		return jdbc.sql("SELECT id FROM app_user WHERE cognito_sub = :sub AND status = 'ACTIVE'")
				.param("sub", cognitoSub)
				.query(UUID.class)
				.optional()
				.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ELIGIBLE",
						"Reporting is available only to active accounts"));
	}

	private record ProfileTarget(UUID userId, boolean discoverable, boolean matched) {
	}

	private record MessageTarget(UUID messageId, UUID senderId) {
	}

	private record PostTarget(UUID postId, UUID authorId) {
	}
}
