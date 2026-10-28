package com.pritimitra.backend.safety;

import com.pritimitra.backend.common.ApiException;
import com.pritimitra.backend.safety.ModerationDtos.ReportQueuePage;
import com.pritimitra.backend.safety.ModerationDtos.ReviewReceipt;
import com.pritimitra.backend.safety.ModerationDtos.ReviewRequest;
import com.pritimitra.backend.safety.ModerationAuditDtos.AuditHistoryPage;
import java.util.Collection;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Staff-only safety tooling. Access is enforced from the verified Cognito group claim, not client UI state. */
@RestController
@RequestMapping("/api/v1/moderation")
class ModerationController {
    static final String MODERATOR_GROUP = "PritiMitraModerators";

    private final ModerationService moderation;
    private final ModerationAuditService auditHistory;

    ModerationController(ModerationService moderation, ModerationAuditService auditHistory) {
        this.moderation = moderation;
        this.auditHistory = auditHistory;
    }

    @GetMapping("/reports")
    ReportQueuePage reports(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "status", defaultValue = "PENDING") String status,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        requireModerator(jwt);
        return moderation.reports(status, cursor, size);
    }

    @GetMapping("/audit")
    AuditHistoryPage auditHistory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "eventType", defaultValue = "ALL") String eventType,
            @RequestParam(name = "from", required = false) OffsetDateTime from,
            @RequestParam(name = "to", required = false) OffsetDateTime to,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        requireModerator(jwt);
        return auditHistory.page(eventType, cursor, from, to, size);
    }

    @GetMapping("/audit/export")
    ResponseEntity<byte[]> exportAuditHistory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "eventType", defaultValue = "ALL") String eventType,
            @RequestParam(name = "from", required = false) OffsetDateTime from,
            @RequestParam(name = "to", required = false) OffsetDateTime to) {
        requireModerator(jwt);
        byte[] csv = auditHistory.exportCsv(eventType, from, to);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"pritimitra-moderation-audit.csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(csv);
    }

    @PutMapping("/reports/{reportPublicId}/decision")
    ReviewReceipt decide(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID reportPublicId,
            @RequestBody ReviewRequest request) {
        requireModerator(jwt);
        return moderation.decide(jwt.getSubject(), reportPublicId, request);
    }

    private static void requireModerator(Jwt jwt) {
        Object claim = jwt.getClaims().get("cognito:groups");
        boolean isModerator = claim instanceof Collection<?> groups
                && groups.stream().anyMatch(MODERATOR_GROUP::equals);
        if (!isModerator) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MODERATOR_REQUIRED",
                    "This action requires the PritiMitra moderation role");
        }
    }
}
