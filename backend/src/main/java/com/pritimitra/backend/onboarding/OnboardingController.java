package com.pritimitra.backend.onboarding;

import com.pritimitra.backend.onboarding.OnboardingDtos.AgeVerificationRequest;
import com.pritimitra.backend.onboarding.OnboardingDtos.ConsentRequest;
import com.pritimitra.backend.onboarding.OnboardingDtos.OnboardingStateResponse;
import com.pritimitra.backend.onboarding.OnboardingDtos.ProfileRequest;
import com.pritimitra.backend.onboarding.OnboardingDtos.ProfileResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
class OnboardingController {

	private final OnboardingService onboarding;

	OnboardingController(OnboardingService onboarding) {
		this.onboarding = onboarding;
	}

	@GetMapping
	OnboardingStateResponse state(@AuthenticationPrincipal Jwt jwt) {
		return onboarding.getState(jwt.getSubject(), jwt.getTokenValue());
	}

	@GetMapping("/profile")
	ProfileResponse profile(@AuthenticationPrincipal Jwt jwt) {
		return onboarding.getProfile(jwt.getSubject(), jwt.getTokenValue());
	}

	@PutMapping("/age-verification")
	ResponseEntity<?> verifyAge(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AgeVerificationRequest request) {
		if (!onboarding.verifyAge(jwt.getSubject(), jwt.getTokenValue(), request.dateOfBirth())) {
			ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
					"PritiMitra is available to adults aged 18 or older");
			problem.setProperty("code", "AGE_INELIGIBLE");
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
		}
		return ResponseEntity.ok(onboarding.getState(jwt.getSubject(), jwt.getTokenValue()));
	}

	@PutMapping("/consents")
	OnboardingStateResponse consents(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConsentRequest request) {
		return onboarding.recordConsents(jwt.getSubject(), jwt.getTokenValue(), request.consents());
	}

	@PutMapping("/profile")
	OnboardingStateResponse profile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest request) {
		return onboarding.updateProfile(jwt.getSubject(), jwt.getTokenValue(), request);
	}

	@PostMapping("/complete")
	OnboardingStateResponse complete(@AuthenticationPrincipal Jwt jwt) {
		return onboarding.complete(jwt.getSubject(), jwt.getTokenValue());
	}
}
