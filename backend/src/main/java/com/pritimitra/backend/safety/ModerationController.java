package com.pritimitra.backend.safety;

import com.pritimitra.backend.safety.ModerationDtos.ReportPage;
import com.pritimitra.backend.safety.ModerationDtos.ReviewReceipt;
import com.pritimitra.backend.safety.ModerationDtos.ReviewRequest;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Staff-only report review. Every call is independently authorized by a Cognito group claim in the service. */
@RestController
@RequestMapping("/api/v1/moderation/reports")
class ModerationController {
    private final ModerationService moderation;

    ModerationController(ModerationService moderation) {
        this.moderation = moderation;
    }

    @GetMapping
    ReportPage list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor) {
        return moderation.list(jwt, status, cursor, size);
    }

    @PutMapping("/{reportPublicId}")
    @ResponseStatus(HttpStatus.OK)
    ReviewReceipt review(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID reportPublicId,
            @RequestBody ReviewRequest request) {
        return moderation.review(jwt, reportPublicId, request);
    }
}
