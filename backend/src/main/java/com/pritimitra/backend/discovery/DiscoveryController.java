package com.pritimitra.backend.discovery;

import com.pritimitra.backend.common.ApiException;
import com.pritimitra.backend.discovery.DiscoveryDtos.DiscoveryPage;
import com.pritimitra.backend.onboarding.ConnectionGoal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Eligible, ACTIVE viewers page through discoverable profiles. Identity comes only from the verified token. */
@RestController
@RequestMapping("/api/v1/discovery")
class DiscoveryController {

	private final DiscoveryService discovery;

	DiscoveryController(DiscoveryService discovery) {
		this.discovery = discovery;
	}

	@GetMapping
	DiscoveryPage list(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "goal", required = false) String goal,
			@RequestParam(name = "size", defaultValue = "" + DiscoveryService.DEFAULT_PAGE_SIZE) int size) {
		return discovery.discover(jwt.getSubject(), cursor, parseGoal(goal), size);
	}

	private static ConnectionGoal parseGoal(String goal) {
		if (goal == null || goal.isBlank()) {
			return null;
		}
		try {
			return ConnectionGoal.valueOf(goal);
		} catch (IllegalArgumentException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GOAL", "Unknown connection goal filter");
		}
	}
}
