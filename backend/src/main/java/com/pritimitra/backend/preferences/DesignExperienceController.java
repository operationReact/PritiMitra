package com.pritimitra.backend.preferences;

import com.pritimitra.backend.preferences.DesignExperienceDtos.DesignExperienceResponse;
import com.pritimitra.backend.preferences.DesignExperienceDtos.UpdateDesignExperienceRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated, owner-only design experience preference. */
@RestController
@RequestMapping("/api/v1/me/preferences/design-experience")
class DesignExperienceController {

	private final DesignExperienceService experiences;

	DesignExperienceController(DesignExperienceService experiences) {
		this.experiences = experiences;
	}

	@GetMapping
	DesignExperienceResponse get(@AuthenticationPrincipal Jwt jwt) {
		return experiences.get(jwt.getSubject());
	}

	@PutMapping
	DesignExperienceResponse update(@AuthenticationPrincipal Jwt jwt,
			@RequestBody UpdateDesignExperienceRequest request) {
		return experiences.update(jwt.getSubject(), request.experience());
	}
}
