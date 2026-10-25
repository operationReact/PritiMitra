package com.pritimitra.backend.discovery;

import com.pritimitra.backend.discovery.DiscoveryDtos.LikeStatus;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Idempotent like and unlike operations keyed by the opaque public profile identifier. */
@RestController
@RequestMapping("/api/v1/likes/{publicId}")
class LikeController {

	private final LikeService likes;

	LikeController(LikeService likes) {
		this.likes = likes;
	}

	@PutMapping
	LikeStatus like(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID publicId) {
		return likes.like(jwt.getSubject(), publicId);
	}

	@DeleteMapping
	LikeStatus unlike(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID publicId) {
		return likes.unlike(jwt.getSubject(), publicId);
	}
}
