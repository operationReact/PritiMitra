package com.pritimitra.backend.discovery;

import com.pritimitra.backend.discovery.MatchDtos.MatchPage;
import com.pritimitra.backend.discovery.MatchDtos.MatchSummary;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated, participant-only match listing, details, and explicit unmatch. Matches are never created here. */
@RestController
@RequestMapping("/api/v1/matches")
class MatchController {

	private final MatchService matches;

	MatchController(MatchService matches) {
		this.matches = matches;
	}

	@GetMapping
	MatchPage list(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "size", defaultValue = "" + DiscoveryService.DEFAULT_PAGE_SIZE) int size) {
		return matches.list(jwt.getSubject(), cursor, size);
	}

	@GetMapping("/{matchPublicId}")
	MatchSummary get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID matchPublicId) {
		return matches.get(jwt.getSubject(), matchPublicId);
	}

	@DeleteMapping("/{matchPublicId}")
	MatchSummary unmatch(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID matchPublicId) {
		return matches.unmatch(jwt.getSubject(), matchPublicId);
	}
}
