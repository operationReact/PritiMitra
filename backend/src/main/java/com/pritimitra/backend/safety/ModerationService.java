package com.pritimitra.backend.safety;

import com.pritimitra.backend.common.ApiException;
import com.pritimitra.backend.safety.ModerationDtos.ReportItem;
import com.pritimitra.backend.safety.ModerationDtos.ReportPage;
import com.pritimitra.backend.safety.ModerationDtos.ReviewReceipt;
import com.pritimitra.backend.safety.ModerationDtos.ReviewRequest;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ModerationService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_NOTE_CODE_POINTS = 500;
    private static final Set<String> MODERATOR_GROUPS = Set.of("PRITIMITRA_MODERATORS", "PRITIMITRA_ADMINS");

    private final JdbcClient jdbc;

    ModerationService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    ReportPage list(Jwt jwt, String rawStatus, String rawCursor, int size) {
        moderatorId(jwt);
        String status = normalizeStatus(rawStatus);
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_SIZE",
                    "Page size must be between 1 and 50");
        }
        Cursor cursor = decodeCursor(rawCursor);
        StringBuilder sql = new StringBuilder("""
                SELECT r.public_id AS report_public_id, r.target_type, r.category, r.details, r.status,
                       r.created_at, COALESCE(tp.public_id, cm.public_id, cp.public_id) AS target_public_id,
                       rp.public_id AS reported_profile_public_id,
                       COALESCE(rp.display_name, 'Unavailable') AS reported_display_name,
                       LEFT(CASE r.target_type
                           WHEN 'PROFILE' THEN COALESCE(tp.bio, tp.profile_prompt, '')
                           WHEN 'MESSAGE' THEN COALESCE(cm.body, '')
                           ELSE COALESCE(cp.body, '')
                       END, 500) AS evidence
                FROM user_report r
                LEFT JOIN onboarding_profile rp ON rp.user_id = r.reported_user_id
                LEFT JOIN onboarding_profile tp ON r.target_type = 'PROFILE' AND tp.user_id = r.reported_user_id
                LEFT JOIN conversation_message cm ON cm.id = r.target_message_id
                LEFT JOIN community_post cp ON cp.id = r.target_post_id
                WHERE r.status = :status
                """);
        if (cursor != null) {
            sql.append(" AND (r.created_at, r.public_id) < (:cursor_at, :cursor_public_id)\n");
        }
        sql.append(" ORDER BY r.created_at DESC, r.public_id DESC LIMIT :limit");
        var statement = jdbc.sql(sql.toString()).param("status", status).param("limit", size + 1);
        if (cursor != null) {
            statement.param("cursor_at", cursor.createdAt()).param("cursor_public_id", cursor.reportPublicId());
        }
        List<ReportItem> fetched = statement.query((rs, row) -> mapReport(rs)).list();
        boolean hasMore = fetched.size() > size;
        List<ReportItem> items = new ArrayList<>(fetched.subList(0, Math.min(size, fetched.size())));
        String nextCursor = hasMore && !items.isEmpty()
                ? encodeCursor(items.get(items.size() - 1).createdAt(), items.get(items.size() - 1).reportId())
                : null;
        return new ReportPage(items, nextCursor, hasMore);
    }

    @Transactional
    ReviewReceipt review(Jwt jwt, UUID reportPublicId, ReviewRequest request) {
        UUID reviewerId = moderatorId(jwt);
        if (reportPublicId == null) {
            throw invalid("reportId is required");
        }
        if (request == null) {
            throw invalid("Review decision is required");
        }
        String decision = normalizeDecision(request.decision());
        String note = normalizeNote(request.note());

        ReviewTarget target = jdbc.sql("""
                SELECT r.id, r.public_id, r.status, r.target_type, r.target_message_id, r.target_post_id,
                       r.reported_user_id, COALESCE(tp.public_id, cm.public_id, cp.public_id) AS target_public_id
                FROM user_report r
                LEFT JOIN onboarding_profile tp ON r.target_type = 'PROFILE' AND tp.user_id = r.reported_user_id
                LEFT JOIN conversation_message cm ON cm.id = r.target_message_id
                LEFT JOIN community_post cp ON cp.id = r.target_post_id
                WHERE r.public_id = :public_id
                FOR UPDATE OF r
                """)
                .param("public_id", reportPublicId)
                .query((rs, row) -> new ReviewTarget(
                        rs.getObject("id", UUID.class),
                        rs.getObject("public_id", UUID.class),
                        rs.getString("status"),
                        rs.getString("target_type"),
                        rs.getObject("target_message_id", UUID.class),
                        rs.getObject("target_post_id", UUID.class),
                        rs.getObject("reported_user_id", UUID.class),
                        rs.getObject("target_public_id", UUID.class)))
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "Report not found"));

        if (!"PENDING".equals(target.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "REPORT_ALREADY_REVIEWED", "This report has already been reviewed");
        }

        String reportStatus;
        String auditAction;
        switch (decision) {
            case "DISMISS" -> {
                reportStatus = "DISMISSED";
                auditAction = "REPORT_DISMISSED";
            }
            case "HIDE_CONTENT" -> {
                if (!"COMMUNITY_POST".equals(target.targetType()) || target.targetPostId() == null) {
                    throw invalid("HIDE_CONTENT is supported only for community-post reports");
                }
                int hidden = jdbc.sql("""
                        UPDATE community_post SET status = 'HIDDEN', updated_at = clock_timestamp()
                        WHERE id = :post_id AND status <> 'REMOVED'
                        """).param("post_id", target.targetPostId()).update();
                if (hidden == 0) {
                    throw new ApiException(HttpStatus.CONFLICT, "REPORT_TARGET_UNAVAILABLE",
                            "The reported post is no longer available for moderation");
                }
                reportStatus = "ACTIONED";
                auditAction = "CONTENT_HIDDEN";
            }
            case "SUSPEND_ACCOUNT" -> {
                if (target.reportedUserId() == null) {
                    throw new ApiException(HttpStatus.CONFLICT, "REPORT_TARGET_UNAVAILABLE",
                            "The reported account is no longer available");
                }
                suspendAccount(target.reportedUserId());
                reportStatus = "ACTIONED";
                auditAction = "ACCOUNT_SUSPENDED";
            }
            default -> throw invalid("Unsupported review decision");
        }

        OffsetDateTime reviewedAt = jdbc.sql("""
                UPDATE user_report
                SET status = :status, reviewed_at = clock_timestamp(), reviewer_id = :reviewer,
                    resolution_action = :action, resolution_note = :note
                WHERE id = :id AND status = 'PENDING'
                RETURNING reviewed_at
                """)
                .param("status", reportStatus)
                .param("reviewer", reviewerId)
                .param("action", decision)
                .param("note", note, java.sql.Types.VARCHAR)
                .param("id", target.id())
                .query((rs, row) -> rs.getObject("reviewed_at", OffsetDateTime.class))
                .single();

        jdbc.sql("""
                INSERT INTO moderation_audit_event
                    (actor_user_id, report_id, action, target_type, target_public_id, target_user_id, note)
                VALUES (:actor, :report, :action, :target_type, :target_public_id, :target_user, :note)
                """)
                .param("actor", reviewerId)
                .param("report", target.id())
                .param("action", auditAction)
                .param("target_type", target.targetType())
                .param("target_public_id", target.targetPublicId(), java.sql.Types.OTHER)
                .param("target_user", target.reportedUserId(), java.sql.Types.OTHER)
                .param("note", note, java.sql.Types.VARCHAR)
                .update();
        return new ReviewReceipt(reportPublicId, reportStatus, decision, reviewedAt);
    }

    private void suspendAccount(UUID targetUserId) {
        int updated = jdbc.sql("""
                UPDATE app_user SET status = 'SUSPENDED', updated_at = clock_timestamp(), version = version + 1
                WHERE id = :target AND status <> 'INELIGIBLE'
                """).param("target", targetUserId).update();
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "REPORT_TARGET_UNAVAILABLE",
                    "The reported account cannot be suspended");
        }
        jdbc.sql("""
                UPDATE onboarding_profile SET discovery_visible = false, updated_at = clock_timestamp()
                WHERE user_id = :target
                """).param("target", targetUserId).update();

        // Close existing access paths so a suspension also takes effect for established conversations and sessions.
        jdbc.sql("""
                UPDATE profile_match
                SET status = 'UNMATCHED', ended_at = clock_timestamp(), updated_at = clock_timestamp()
                WHERE status = 'ACTIVE' AND (user_low_id = :target OR user_high_id = :target)
                """).param("target", targetUserId).update();
        jdbc.sql("""
                UPDATE community_post SET status = 'HIDDEN', updated_at = clock_timestamp()
                WHERE author_id = :target AND status = 'PUBLISHED'
                """).param("target", targetUserId).update();
        jdbc.sql("""
                UPDATE blind_date_opt_in SET status = 'WITHDRAWN', pair_id = NULL, updated_at = clock_timestamp()
                WHERE pair_id IN (SELECT id FROM blind_date_pair
                                  WHERE status = 'SCHEDULED' AND (user_low_id = :target OR user_high_id = :target))
                  AND status = 'PAIRED'
                """).param("target", targetUserId).update();
        jdbc.sql("""
                UPDATE blind_date_pair SET status = 'CANCELLED', updated_at = clock_timestamp()
                WHERE status = 'SCHEDULED' AND (user_low_id = :target OR user_high_id = :target)
                """).param("target", targetUserId).update();
        jdbc.sql("""
                UPDATE blind_date_opt_in SET status = 'WITHDRAWN', pair_id = NULL, updated_at = clock_timestamp()
                WHERE user_id = :target AND status IN ('WAITING', 'PAIRED')
                """).param("target", targetUserId).update();
    }

    private UUID moderatorId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || !hasModeratorGroup(jwt)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MODERATION_ACCESS_DENIED",
                    "A PritiMitra moderator account is required");
        }
        return jdbc.sql("""
                SELECT id FROM app_user
                WHERE cognito_sub = :sub AND status NOT IN ('SUSPENDED', 'INELIGIBLE')
                """).param("sub", jwt.getSubject()).query(UUID.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "MODERATOR_ACCOUNT_NOT_AVAILABLE",
                        "The moderator account is unavailable"));
    }

    private static boolean hasModeratorGroup(Jwt jwt) {
        Object claim = jwt.getClaims().get("cognito:groups");
        if (!(claim instanceof Collection<?> groups)) {
            return false;
        }
        return groups.stream().filter(String.class::isInstance).map(String.class::cast)
                .anyMatch(MODERATOR_GROUPS::contains);
    }

    private static String normalizeStatus(String raw) {
        if (raw == null) {
            return "PENDING";
        }
        String status = raw.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PENDING", "ACTIONED", "DISMISSED").contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REPORT_STATUS",
                    "status must be PENDING, ACTIONED or DISMISSED");
        }
        return status;
    }

    private static String normalizeDecision(String raw) {
        if (raw == null) {
            throw invalid("decision is required");
        }
        String decision = raw.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("DISMISS", "HIDE_CONTENT", "SUSPEND_ACCOUNT").contains(decision)) {
            throw invalid("decision must be DISMISS, HIDE_CONTENT or SUSPEND_ACCOUNT");
        }
        return decision;
    }

    private static String normalizeNote(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String note = raw.trim();
        if (note.codePointCount(0, note.length()) > MAX_NOTE_CODE_POINTS
                || note.codePoints().anyMatch(cp -> cp == 0 || (Character.isISOControl(cp) && cp != '\n' && cp != '\t'))) {
            throw invalid("note must be at most 500 characters and contain no unsupported control characters");
        }
        return note;
    }

    private static Cursor decodeCursor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.length() > 128 || !raw.matches("[A-Za-z0-9_-]+")) {
            throw invalidCursor();
        }
        try {
            String value = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
            int separator = value.lastIndexOf('|');
            if (separator <= 0 || separator == value.length() - 1) {
                throw invalidCursor();
            }
            return new Cursor(OffsetDateTime.parse(value.substring(0, separator)),
                    UUID.fromString(value.substring(separator + 1)));
        } catch (IllegalArgumentException | java.time.DateTimeException ex) {
            throw invalidCursor();
        }
    }

    private static String encodeCursor(OffsetDateTime createdAt, UUID reportPublicId) {
        String value = createdAt.toString() + "|" + reportPublicId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static ReportItem mapReport(ResultSet rs) throws SQLException {
        return new ReportItem(
                rs.getObject("report_public_id", UUID.class),
                rs.getString("target_type"),
                rs.getObject("target_public_id", UUID.class),
                rs.getString("category"),
                rs.getString("details"),
                rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("reported_profile_public_id", UUID.class),
                rs.getString("reported_display_name"),
                rs.getString("evidence"));
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MODERATION_REQUEST", message);
    }

    private static ApiException invalidCursor() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "Pagination cursor is invalid");
    }

    private record Cursor(OffsetDateTime createdAt, UUID reportPublicId) {}
    private record ReviewTarget(UUID id, UUID publicId, String status, String targetType,
            UUID targetMessageId, UUID targetPostId, UUID reportedUserId, UUID targetPublicId) {}
}
