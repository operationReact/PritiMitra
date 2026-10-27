package com.pritimitra.backend.safety;

import com.pritimitra.backend.common.ApiException;
import com.pritimitra.backend.safety.ModerationDtos.ReportQueuePage;
import com.pritimitra.backend.safety.ModerationDtos.ReviewReceipt;
import com.pritimitra.backend.safety.ModerationDtos.ReviewRequest;
import java.util.Collection;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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

    ModerationController(ModerationService moderation) {
        this.moderation = moderation;
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
