package com.pritimitra.backend.safety;

import com.pritimitra.backend.common.ApiException;
import com.pritimitra.backend.common.PublicIdCursor;
import com.pritimitra.backend.safety.ModerationDtos.ReportQueueItem;
import com.pritimitra.backend.safety.ModerationDtos.ReportQueuePage;
import com.pritimitra.backend.safety.ModerationDtos.ReviewReceipt;
import com.pritimitra.backend.safety.ModerationDtos.ReviewRequest;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ModerationService {
    private static final int MAX_PAGE_SIZE = 50;
    private final JdbcClient jdbc;

    ModerationService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    ReportQueuePage reports(String rawStatus, String rawCursor, int size) {
        String status = normalizeStatus(rawStatus);
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_SIZE",
                    "Page size must be between 1 and 50");
        }
        UUID cursor = PublicIdCursor.decode(rawCursor);
        if (cursor != null) {
            boolean exists = jdbc.sql("SELECT EXISTS(SELECT 1 FROM user_report WHERE public_id=:cursor)")
                    .param("cursor", cursor).query(Boolean.class).single();
            if (!exists) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "Pagination cursor is invalid");
            }
        }

        List<ReportQueueItem> rows = jdbc.sql("""
                SELECT r.public_id AS report_id, r.target_type, r.category, r.details, r.status, r.created_at,
                       r.decision AS review_decision, r.review_note, r.reviewed_at,
                       reporter_profile.public_id AS reporter_profile_public_id,
                       target_profile.public_id AS reported_profile_public_id,
                       CASE r.target_type
                         WHEN 'PROFILE' THEN target_profile.public_id
                         WHEN 'MESSAGE' THEN message.public_id
                         WHEN 'COMMUNITY_POST' THEN post.public_id
                       END AS target_public_id,
                       CASE r.target_type
                         WHEN 'PROFILE' THEN concat_ws(' — ',
                             NULLIF(target_profile.display_name, ''), NULLIF(target_profile.bio, ''))
                         WHEN 'MESSAGE' THEN message.body
                         WHEN 'COMMUNITY_POST' THEN post.body
                       END AS target_preview,
                       COALESCE(reviewer_profile.display_name, 'Moderator') AS reviewer_display_name
                FROM user_report r
                LEFT JOIN onboarding_profile reporter_profile ON reporter_profile.user_id = r.reporter_id
                LEFT JOIN onboarding_profile target_profile ON target_profile.user_id = r.reported_user_id
                LEFT JOIN conversation_message message ON message.id = r.target_message_id
                LEFT JOIN community_post post ON post.id = r.target_post_id
                LEFT JOIN app_user reviewer_user ON reviewer_user.cognito_sub = r.reviewed_by_sub
                LEFT JOIN onboarding_profile reviewer_profile ON reviewer_profile.user_id = reviewer_user.id
                WHERE (:status = 'ALL' OR r.status = :status)
                  AND (CAST(:cursor AS uuid) IS NULL OR (r.created_at, r.public_id) <
                      (SELECT c.created_at, c.public_id FROM user_report c
                       WHERE c.public_id = CAST(:cursor AS uuid)))
                ORDER BY r.created_at DESC, r.public_id DESC
                LIMIT :limit
                """)
                .param("status", status)
                .param("cursor", cursor, Types.OTHER)
                .param("limit", size + 1)
                .query((rs, n) -> new ReportQueueItem(
                        rs.getObject("report_id", UUID.class),
                        rs.getString("target_type"),
                        rs.getObject("target_public_id", UUID.class),
                        rs.getObject("reporter_profile_public_id", UUID.class),
                        rs.getObject("reported_profile_public_id", UUID.class),
                        rs.getString("category"),
                        rs.getString("details"),
                        rs.getString("target_preview"),
                        rs.getString("status"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getString("review_decision"),
                        rs.getString("review_note"),
                        rs.getObject("reviewed_at", OffsetDateTime.class),
                        rs.getString("reviewer_display_name")))
                .list();

        if (rows.size() <= size) {
            return new ReportQueuePage(rows, null);
        }
        List<ReportQueueItem> page = rows.subList(0, size);
        return new ReportQueuePage(page, PublicIdCursor.encode(page.get(page.size() - 1).reportId()));
    }

    @Transactional
    ReviewReceipt decide(String actorSub, UUID reportPublicId, ReviewRequest request) {
        String decision = normalizeDecision(request == null ? null : request.decision());
        String note = normalizeNote(request == null ? null : request.note());
        if ("SUSPEND_ACCOUNT".equals(decision) && note == null) {
            throw invalid("A moderator note is required to suspend an account");
        }

        StoredReport report = jdbc.sql("""
                SELECT id, public_id, status, target_type, target_message_id, target_post_id, reported_user_id
                FROM user_report WHERE public_id = :publicId FOR UPDATE
                """)
                .param("publicId", reportPublicId)
                .query((rs, n) -> new StoredReport(
                        rs.getObject("id", UUID.class),
                        rs.getObject("public_id", UUID.class),
                        rs.getString("status"),
                        rs.getString("target_type"),
                        rs.getObject("target_message_id", UUID.class),
                        rs.getObject("target_post_id", UUID.class),
                        rs.getObject("reported_user_id", UUID.class)))
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "Report not found"));

        if (!"PENDING".equals(report.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "REPORT_ALREADY_REVIEWED",
                    "This report has already been reviewed");
        }
        validateDecisionTarget(decision, report.targetType());

        UUID targetPublicId = targetPublicId(report);
        String eventType;
        if ("DISMISS".equals(decision)) {
            eventType = "REPORT_DISMISSED";
        } else if ("HIDE_POST".equals(decision)) {
            if (report.postId() == null) {
                throw invalid("The reported post is no longer available");
            }
            jdbc.sql("""
                    UPDATE community_post SET status = 'HIDDEN', updated_at = clock_timestamp()
                    WHERE id = :id AND status <> 'REMOVED'
                    """).param("id", report.postId()).update();
            eventType = "COMMUNITY_POST_HIDDEN";
        } else if ("HIDE_PROFILE".equals(decision)) {
            if (report.reportedUserId() == null) {
                throw invalid("The reported profile is no longer available");
            }
            int rows = jdbc.sql("""
                    UPDATE onboarding_profile SET discovery_visible = false, updated_at = clock_timestamp()
                    WHERE user_id = :id
                    """).param("id", report.reportedUserId()).update();
            if (rows == 0) {
                throw invalid("The reported profile is no longer available");
            }
            eventType = "PROFILE_HIDDEN";
        } else {
            if (report.reportedUserId() == null) {
                throw invalid("The reported account is no longer available");
            }
            int rows = jdbc.sql("""
                    UPDATE app_user SET status = 'SUSPENDED', updated_at = clock_timestamp(), version = version + 1
                    WHERE id = :id AND status <> 'INELIGIBLE'
                    """).param("id", report.reportedUserId()).update();
            if (rows == 0) {
                throw invalid("The reported account is not eligible for suspension");
            }
            eventType = "ACCOUNT_SUSPENDED";
        }

        String nextStatus = "DISMISS".equals(decision) ? "DISMISSED" : "ACTIONED";
        ReviewReceipt receipt = jdbc.sql("""
                UPDATE user_report SET status = :status, reviewed_by_sub = :actorSub,
                    reviewed_at = clock_timestamp(), decision = :decision, review_note = :note
                WHERE id = :id
                RETURNING public_id, status, decision, reviewed_at
                """)
                .param("status", nextStatus)
                .param("actorSub", actorSub)
                .param("decision", decision)
                .param("note", note, Types.VARCHAR)
                .param("id", report.id())
                .query((rs, n) -> new ReviewReceipt(
                        rs.getObject("public_id", UUID.class),
                        rs.getString("status"),
                        rs.getString("decision"),
                        rs.getObject("reviewed_at", OffsetDateTime.class)))
                .single();

        jdbc.sql("""
                INSERT INTO safety_audit_event
                    (actor_sub, event_type, report_public_id, target_type, target_public_id, note)
                VALUES (:actor, :eventType, :reportPublicId, :targetType, :targetPublicId, :note)
                """)
                .param("actor", actorSub)
                .param("eventType", eventType)
                .param("reportPublicId", report.publicId())
                .param("targetType", report.targetType())
                .param("targetPublicId", targetPublicId, Types.OTHER)
                .param("note", note, Types.VARCHAR)
                .update();

        return receipt;
    }

    private UUID targetPublicId(StoredReport report) {
        return switch (report.targetType()) {
            case "PROFILE" -> report.reportedUserId() == null ? null
                    : jdbc.sql("SELECT public_id FROM onboarding_profile WHERE user_id = :id")
                        .param("id", report.reportedUserId()).query(UUID.class).optional().orElse(null);
            case "MESSAGE" -> report.messageId() == null ? null
                    : jdbc.sql("SELECT public_id FROM conversation_message WHERE id = :id")
                        .param("id", report.messageId()).query(UUID.class).optional().orElse(null);
            case "COMMUNITY_POST" -> report.postId() == null ? null
                    : jdbc.sql("SELECT public_id FROM community_post WHERE id = :id")
                        .param("id", report.postId()).query(UUID.class).optional().orElse(null);
            default -> null;
        };
    }

    private static void validateDecisionTarget(String decision, String targetType) {
        if ("HIDE_POST".equals(decision) && !"COMMUNITY_POST".equals(targetType)) {
            throw invalid("HIDE_POST can only be used for a community post report");
        }
        if ("HIDE_PROFILE".equals(decision) && !"PROFILE".equals(targetType)) {
            throw invalid("HIDE_PROFILE can only be used for a profile report");
        }
    }

    private static String normalizeStatus(String raw) {
        if (raw == null) {
            return "PENDING";
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "PENDING", "ACTIONED", "DISMISSED", "ALL" -> value;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_STATUS",
                    "status must be PENDING, ACTIONED, DISMISSED or ALL");
        };
    }

    private static String normalizeDecision(String raw) {
        if (raw == null) {
            throw invalid("decision is required");
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "DISMISS", "HIDE_POST", "HIDE_PROFILE", "SUSPEND_ACCOUNT" -> value;
            default -> throw invalid("decision is not supported");
        };
    }

    private static String normalizeNote(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.codePointCount(0, value.length()) > 500) {
            throw invalid("Moderator notes must be at most 500 characters");
        }
        return value;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MODERATION_DECISION", message);
    }

    private record StoredReport(
            UUID id, UUID publicId, String status, String targetType, UUID messageId, UUID postId, UUID reportedUserId) {}
}
