package com.pritimitra.backend.identity;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/account")
class AccountController {

    private final AccountDeletionService deletions;

    AccountController(AccountDeletionService deletions) {
        this.deletions = deletions;
    }

    @DeleteMapping
    ResponseEntity<AccountDeletionResponse> deleteAccount(@AuthenticationPrincipal Jwt jwt) {
        AccountDeletionResponse response = deletions.requestDeletion(jwt.getSubject());
        if ("COMPLETED".equals(response.status())) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/consents")
    AccountDeletionService.ConsentState consents(@AuthenticationPrincipal Jwt jwt) {
        return deletions.getOptionalConsent(jwt.getSubject());
    }

    @PutMapping("/consents/community-nickname")
    AccountDeletionService.ConsentState updateCommunityNicknameConsent(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OptionalConsentRequest request) {
        deletions.recordOptionalConsent(jwt.getSubject(), request.accepted());
        return deletions.getOptionalConsent(jwt.getSubject());
    }

    record OptionalConsentRequest(@NotNull Boolean accepted) {}
}
