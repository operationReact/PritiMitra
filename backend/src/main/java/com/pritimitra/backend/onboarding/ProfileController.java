package com.pritimitra.backend.onboarding;

import com.pritimitra.backend.onboarding.OnboardingDtos.DiscoverabilityRequest;
import com.pritimitra.backend.onboarding.OnboardingDtos.OwnProfileResponse;
import com.pritimitra.backend.onboarding.OnboardingDtos.ProfileRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner-only profile operations. Identity comes from the verified token; no account id is accepted from clients. */
@RestController
@RequestMapping("/api/v1/profile")
class ProfileController {

	private final OnboardingService onboarding;

	ProfileController(OnboardingService onboarding) {
		this.onboarding = onboarding;
	}

	@GetMapping
	OwnProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
		return onboarding.getOwnProfile(jwt.getSubject(), jwt.getTokenValue());
	}

	@PutMapping
	OwnProfileResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileRequest request) {
		return onboarding.updateOwnProfile(jwt.getSubject(), jwt.getTokenValue(), request);
	}

	@PutMapping("/discoverability")
	OwnProfileResponse discoverability(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody DiscoverabilityRequest request) {
		return onboarding.setDiscoverable(jwt.getSubject(), jwt.getTokenValue(), request.discoverable());
	}
}
