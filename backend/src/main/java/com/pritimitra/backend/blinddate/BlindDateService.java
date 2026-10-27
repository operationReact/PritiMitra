package com.pritimitra.backend.blinddate;

import com.pritimitra.backend.blinddate.BlindDateDtos.*;
import com.pritimitra.backend.common.ApiException;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class BlindDateService {
    private static final long SLOT_SECONDS = 900L;
    private static final int MAX_ITEMS = 20;
    private final JdbcClient jdbc;
    BlindDateService(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    OptInList mine(String sub) {
        UUID viewer = activeViewerId(sub);
        List<OptInReceipt> items = jdbc.sql(receiptSql() + """
                WHERE b.user_id = :viewer ORDER BY b.starts_at DESC, b.created_at DESC LIMIT :limit
                """).param("viewer", viewer).param("limit", MAX_ITEMS)
                .query((rs,n) -> receipt(rs)).list();
        return new OptInList(items);
    }

    @Transactional
    OptInReceipt optIn(String sub, OptInRequest request) {
        UUID viewer = activeViewerId(sub);
        if (request == null || request.startsAt() == null) throw invalid("startsAt is required");
        String zone = normalizeTimeZone(request.timeZone());
        Instant start = request.startsAt().toInstant();
        Instant now = Instant.now();
        if (!start.isAfter(now.plusSeconds(900)) || start.isAfter(now.plusSeconds(14 * 86400L)))
            throw invalid("Choose a time at least 15 minutes ahead and within 14 days");
        if (start.getEpochSecond() % SLOT_SECONDS != 0 || start.getNano() != 0)
            throw invalid("Blind Date slots must start on a 15-minute boundary");
        OffsetDateTime startsAt = OffsetDateTime.ofInstant(start, ZoneOffset.UTC);

        // A per-slot transaction lock ensures only one pairing decision runs for a given start time.
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:slot AS text), 0))")
                .param("slot", startsAt.toString()).query((rs,n) -> Boolean.TRUE).single();

        Optional<Existing> existing = jdbc.sql("""
                SELECT public_id, status FROM blind_date_opt_in WHERE user_id = :viewer AND starts_at = :startsAt
                """).param("viewer", viewer).param("startsAt", startsAt)
                .query((rs,n) -> new Existing(rs.getObject("public_id", UUID.class), rs.getString("status"))).optional();
        UUID optInId;
        if (existing.isPresent()) {
            if (!"WITHDRAWN".equals(existing.get().status()))
                throw new ApiException(HttpStatus.CONFLICT, "BLIND_DATE_ALREADY_OPTED_IN", "You already opted into this time slot");
            jdbc.sql("""
                    UPDATE blind_date_opt_in SET time_zone = :zone, status = 'WAITING', pair_id = NULL,
                        created_at = now(), updated_at = now()
                    WHERE user_id = :viewer AND starts_at = :startsAt AND status = 'WITHDRAWN'
                    """).param("zone", zone).param("viewer", viewer).param("startsAt", startsAt).update();
            optInId = existing.get().publicId();
        } else {
            optInId = jdbc.sql("""
                    INSERT INTO blind_date_opt_in (user_id, starts_at, time_zone) VALUES (:viewer, :startsAt, :zone)
                    RETURNING public_id
                    """).param("viewer", viewer).param("startsAt", startsAt).param("zone", zone)
                    .query(UUID.class).single();
        }

        Optional<Candidate> candidate = jdbc.sql("""
                SELECT b.id, b.user_id
                FROM blind_date_opt_in b
                JOIN app_user u ON u.id = b.user_id
                JOIN onboarding_profile p ON p.user_id = b.user_id
                WHERE b.starts_at = :startsAt AND b.status = 'WAITING' AND b.user_id <> :viewer
                  AND u.status = 'ACTIVE' AND p.completed_at IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM user_block x
                    WHERE (x.blocker_id = :viewer AND x.blocked_id = b.user_id)
                       OR (x.blocker_id = b.user_id AND x.blocked_id = :viewer))
                  AND NOT EXISTS (SELECT 1 FROM profile_like l
                    WHERE (l.from_user_id = :viewer AND l.to_user_id = b.user_id)
                       OR (l.from_user_id = b.user_id AND l.to_user_id = :viewer))
                  AND NOT EXISTS (SELECT 1 FROM profile_match m WHERE m.status = 'ACTIVE'
                    AND ((m.user_low_id = :viewer AND m.user_high_id = b.user_id)
                      OR (m.user_low_id = b.user_id AND m.user_high_id = :viewer))
                  ORDER BY b.created_at, b.id LIMIT 1 FOR UPDATE OF b SKIP LOCKED
                """).param("startsAt", startsAt).param("viewer", viewer)
                .query((rs,n) -> new Candidate(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class))).optional();

        if (candidate.isPresent()) {
            UUID other = candidate.get().userId();
            UUID low = viewer.compareTo(other) < 0 ? viewer : other;
            UUID high = viewer.compareTo(other) < 0 ? other : viewer;
            UUID pairId = jdbc.sql("""
                    INSERT INTO blind_date_pair (user_low_id, user_high_id, starts_at)
                    VALUES (:low, :high, :startsAt)
                    ON CONFLICT (user_low_id, user_high_id, starts_at)
                    DO UPDATE SET status = 'SCHEDULED', updated_at = now()
                    WHERE blind_date_pair.status = 'CANCELLED'
                    RETURNING id
                    """).param("low", low).param("high", high).param("startsAt", startsAt)
                    .query(UUID.class).optional()
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                            "BLIND_DATE_SLOT_UNAVAILABLE", "That Blind Date slot is no longer available"));
            jdbc.sql("""
                    UPDATE blind_date_opt_in SET status = 'PAIRED', pair_id = :pairId, updated_at = now()
                    WHERE id = :bookingId AND status = 'WAITING'
                    """).param("pairId", pairId).param("bookingId", candidate.get().bookingId()).update();
            jdbc.sql("""
                    UPDATE blind_date_opt_in SET status = 'PAIRED', pair_id = :pairId, updated_at = now()
                    WHERE public_id = :publicId AND user_id = :viewer AND status = 'WAITING'
                    """).param("pairId", pairId).param("publicId", optInId).param("viewer", viewer).update();
        }
        return receiptFor(viewer, optInId);
    }

    @Transactional
    void withdraw(String sub, UUID optInPublicId) {
        UUID viewer = activeViewerId(sub);
        Optional<String> status = jdbc.sql("""
                SELECT status FROM blind_date_opt_in WHERE public_id = :id AND user_id = :viewer
                """).param("id", optInPublicId).param("viewer", viewer).query(String.class).optional();
        if (status.isEmpty() || "WITHDRAWN".equals(status.get())) return;
        if (!"WAITING".equals(status.get()))
            throw new ApiException(HttpStatus.CONFLICT, "BLIND_DATE_ALREADY_PAIRED",
                    "Cancel the scheduled session instead of withdrawing this opt-in");
        jdbc.sql("""
                UPDATE blind_date_opt_in SET status = 'WITHDRAWN', updated_at = now()
                WHERE public_id = :id AND user_id = :viewer AND status = 'WAITING'
                """).param("id", optInPublicId).param("viewer", viewer).update();
    }

    @Transactional
    void cancelSession(String sub, UUID sessionPublicId) {
        UUID viewer = activeViewerId(sub);
        Optional<UUID> pairId = jdbc.sql("""
                UPDATE blind_date_pair SET status = 'CANCELLED', updated_at = now()
                WHERE public_id = :id AND starts_at > now() AND status = 'SCHEDULED'
                  AND (user_low_id = :viewer OR user_high_id = :viewer)
                RETURNING id
                """).param("id", sessionPublicId).param("viewer", viewer).query(UUID.class).optional();
        pairId.ifPresent(id -> jdbc.sql("""
                UPDATE blind_date_opt_in SET status = 'WITHDRAWN', updated_at = now()
                WHERE pair_id = :pairId AND status = 'PAIRED'
                """).param("pairId", id).update());
    }

    private OptInReceipt receiptFor(UUID viewer, UUID publicId) {
        return jdbc.sql(receiptSql() + " WHERE b.public_id = :id AND b.user_id = :viewer")
                .param("id", publicId).param("viewer", viewer).query((rs,n) -> receipt(rs)).single();
    }

    private static String receiptSql() {
        return """
                SELECT b.public_id AS opt_in_id, b.starts_at, b.time_zone, b.status, s.public_id AS session_id,
                       s.status AS session_status, b.created_at,
                       CASE WHEN s.status = 'SCHEDULED'
                         THEN CASE WHEN s.user_low_id = b.user_id THEN high_profile.public_id ELSE low_profile.public_id END
                         ELSE NULL END AS partner_profile_public_id
                FROM blind_date_opt_in b
                LEFT JOIN blind_date_pair s ON s.id = b.pair_id
                LEFT JOIN onboarding_profile low_profile ON low_profile.user_id = s.user_low_id
                LEFT JOIN onboarding_profile high_profile ON high_profile.user_id = s.user_high_id
                """;
    }
    private static OptInReceipt receipt(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OptInReceipt(rs.getObject("opt_in_id", UUID.class), rs.getObject("starts_at", OffsetDateTime.class),
                rs.getString("time_zone"), rs.getString("status"), rs.getObject("session_id", UUID.class),
                rs.getObject("partner_profile_public_id", UUID.class), rs.getString("session_status"),
                rs.getObject("created_at", OffsetDateTime.class));
    }
    private static String normalizeTimeZone(String raw) {
        if (raw == null || raw.isBlank() || raw.trim().length() > 64) throw invalid("timeZone must be a valid IANA time-zone identifier");
        try { ZoneId.of(raw.trim()); } catch (DateTimeException ex) { throw invalid("timeZone must be a valid IANA time-zone identifier"); }
        return raw.trim();
    }
    private UUID activeViewerId(String sub) {
        return jdbc.sql("""
                SELECT u.id FROM app_user u JOIN onboarding_profile p ON p.user_id = u.id
                WHERE u.cognito_sub = :sub AND u.status = 'ACTIVE' AND p.completed_at IS NOT NULL
                """).param("sub", sub).query(UUID.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ELIGIBLE",
                        "Blind Date is available only to active, fully onboarded accounts"));
    }
    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BLIND_DATE_SLOT", message);
    }
    private record Existing(UUID publicId, String status) {}
    private record Candidate(UUID bookingId, UUID userId) {}
}
