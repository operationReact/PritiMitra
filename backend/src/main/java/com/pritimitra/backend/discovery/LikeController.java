package com.pritimitra.backend.discovery;

import com.pritimitra.backend.discovery.DiscoveryDtos.LikeStatus;
import com.pritimitra.backend.discovery.DiscoveryDtos.LikedProfilePage;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Idempotent like and unlike operations keyed by the opaque public profile identifier, plus the viewer's outgoing likes. */
@RestController
@RequestMapping("/api/v1/likes")
class LikeController {

	private final LikeService likes;

	LikeController(LikeService likes) {
		this.likes = likes;
	}

	@GetMapping
	LikedProfilePage outgoing(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "size", defaultValue = "" + DiscoveryService.DEFAULT_PAGE_SIZE) int size) {
		return likes.outgoing(jwt.getSubject(), cursor, size);
	}

	@PutMapping("/{publicId}")
	LikeStatus like(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID publicId) {
		return likes.like(jwt.getSubject(), publicId);
	}

	@DeleteMapping("/{publicId}")
	LikeStatus unlike(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID publicId) {
		return likes.unlike(jwt.getSubject(), publicId);
	}
}

