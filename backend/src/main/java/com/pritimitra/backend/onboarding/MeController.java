package com.pritimitra.backend.onboarding;

import com.pritimitra.backend.onboarding.OnboardingDtos.MeResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class MeController {

	private final OnboardingService onboarding;

	MeController(OnboardingService onboarding) {
		this.onboarding = onboarding;
	}

	@GetMapping("/me")
	MeResponse me(@AuthenticationPrincipal Jwt jwt) {
		return onboarding.getMe(jwt.getSubject(), jwt.getTokenValue());
	}
}
