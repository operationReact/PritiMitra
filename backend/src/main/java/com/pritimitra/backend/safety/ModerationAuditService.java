package com.pritimitra.backend.safety;

import com.pritimitra.backend.common.ApiException;
import com.pritimitra.backend.safety.ModerationAuditDtos.AuditHistoryItem;
import com.pritimitra.backend.safety.ModerationAuditDtos.AuditHistoryPage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ModerationAuditService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_EXPORT_ROWS = 2_000;
    private static final int MAX_RANGE_DAYS = 90;
    private static final int DEFAULT_RANGE_DAYS = 30;

    private final JdbcClient jdbc;

    ModerationAuditService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    AuditHistoryPage page(String rawEventType, String rawCursor, OffsetDateTime rawFrom,
            OffsetDateTime rawTo, int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_SIZE",
                    "Audit page size must be between 1 and 50");
        }
        String eventType = normalizeEventType(rawEventType);
        TimeRange range = normalizeRange(rawFrom, rawTo);
        AuditCursor cursor = decodeCursor(rawCursor);
        List<AuditRow> rows = query(eventType, range, cursor, size + 1);
        if (rows.size() <= size) {
            return new AuditHistoryPage(rows.stream().map(this::toItem).toList(), null, range.from(), range.to());
        }
        List<AuditRow> page = rows.subList(0, size);
        AuditRow last = page.get(page.size() - 1);
        return new AuditHistoryPage(page.stream().map(this::toItem).toList(),
                encodeCursor(last.createdAt(), last.eventId()), range.from(), range.to());
    }

    @Transactional(readOnly = true)
    byte[] exportCsv(String rawEventType, OffsetDateTime rawFrom, OffsetDateTime rawTo) {
        String eventType = normalizeEventType(rawEventType);
        TimeRange range = normalizeRange(rawFrom, rawTo);
        List<AuditRow> rows = query(eventType, range, null, MAX_EXPORT_ROWS + 1);
        if (rows.size() > MAX_EXPORT_ROWS) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "AUDIT_EXPORT_TOO_LARGE",
                    "The export exceeds 2,000 events. Narrow the date range or event filter and try again.");
        }
        StringBuilder csv = new StringBuilder(256 + rows.size() * 220);
        csv.append("Event ID,Occurred at,Actor reference,Event type,Report ID,Target type,Target ID,Moderator note\r\n");
        for (AuditRow row : rows) {
            csv.append(csvCell(row.eventId().toString())).append(',')
                    .append(csvCell(row.createdAt().toString())).append(',')
                    .append(csvCell(actorReference(row.actorSub()))).append(',')
                    .append(csvCell(row.eventType())).append(',')
                    .append(csvCell(row.reportPublicId().toString())).append(',')
                    .append(csvCell(row.targetType())).append(',')
                    .append(csvCell(row.targetPublicId() == null ? "" : row.targetPublicId().toString())).append(',')
                    .append(csvCell(row.note())).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<AuditRow> query(String eventType, TimeRange range, AuditCursor cursor, int limit) {
        return jdbc.sql("""
                SELECT id, actor_sub, event_type, report_public_id, target_type, target_public_id, note, created_at
                FROM safety_audit_event
                WHERE (:eventType = 'ALL' OR event_type = :eventType)
                  AND created_at >= :startAt AND created_at < :endAt
                  AND (CAST(:cursorAt AS timestamptz) IS NULL
                       OR (created_at, id) < (CAST(:cursorAt AS timestamptz), CAST(:cursorId AS uuid)))
                ORDER BY created_at DESC, id DESC
                LIMIT :limit
                """)
                .param("eventType", eventType)
                .param("startAt", range.from(), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("endAt", range.to(), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("cursorAt", cursor == null ? null : cursor.createdAt(), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("cursorId", cursor == null ? null : cursor.eventId(), Types.OTHER)
                .param("limit", limit)
                .query((rs, n) -> new AuditRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("actor_sub"),
                        rs.getString("event_type"),
                        rs.getObject("report_public_id", UUID.class),
                        rs.getString("target_type"),
                        rs.getObject("target_public_id", UUID.class),
                        rs.getString("note"),
                        rs.getObject("created_at", OffsetDateTime.class)))
                .list();
    }

    private AuditHistoryItem toItem(AuditRow row) {
        return new AuditHistoryItem(row.eventId(), actorReference(row.actorSub()), row.eventType(),
                row.reportPublicId(), row.targetType(), row.targetPublicId(), row.note(), row.createdAt());
    }

    private static String normalizeEventType(String raw) {
        if (raw == null || raw.isBlank()) return "ALL";
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "ALL", "REPORT_DISMISSED", "COMMUNITY_POST_HIDDEN", "PROFILE_HIDDEN", "ACCOUNT_SUSPENDED" -> value;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AUDIT_EVENT_TYPE",
                    "eventType is not a supported moderation audit event");
        };
    }

    private static TimeRange normalizeRange(OffsetDateTime rawFrom, OffsetDateTime rawTo) {
        OffsetDateTime now = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        OffsetDateTime end = rawTo == null ? now : rawTo.withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime start = rawFrom == null
                ? end.minusDays(DEFAULT_RANGE_DAYS)
                : rawFrom.withOffsetSameInstant(ZoneOffset.UTC);
        if (!start.isBefore(end) || end.isAfter(now.plusMinutes(1))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AUDIT_RANGE",
                    "Audit date range must have a start before its end and may not be in the future");
        }
        if (start.isBefore(end.minusDays(MAX_RANGE_DAYS))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIT_RANGE_TOO_LARGE",
                    "Audit history is limited to a 90-day date range");
        }
        return new TimeRange(start, end);
    }

    private static AuditCursor decodeCursor(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.length() > 128) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AUDIT_CURSOR", "Audit pagination cursor is invalid");
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
            int separator = decoded.indexOf('|');
            if (separator <= 0 || separator == decoded.length() - 1) throw new IllegalArgumentException();
            OffsetDateTime createdAt = OffsetDateTime.ofInstant(Instant.parse(decoded.substring(0, separator)), ZoneOffset.UTC);
            UUID id = UUID.fromString(decoded.substring(separator + 1));
            return new AuditCursor(createdAt, id);
        } catch (RuntimeException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AUDIT_CURSOR", "Audit pagination cursor is invalid");
        }
    }

    private static String encodeCursor(OffsetDateTime createdAt, UUID id) {
        String raw = createdAt.toInstant().toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String actorReference(String actorSub) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(actorSub.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the runtime", impossible);
        }
    }

    private static String csvCell(String raw) {
        String value = raw == null ? "" : raw;
        String stripped = value.stripLeading();
        boolean dangerousPrefix = (!value.isEmpty() && "\t\r\n".indexOf(value.charAt(0)) >= 0)
                || (!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0);
        if (dangerousPrefix) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private record TimeRange(OffsetDateTime from, OffsetDateTime to) {}
    private record AuditCursor(OffsetDateTime createdAt, UUID eventId) {}
    private record AuditRow(UUID eventId, String actorSub, String eventType, UUID reportPublicId,
            String targetType, UUID targetPublicId, String note, OffsetDateTime createdAt) {}
}
