package com.pritimitra.backend.safety;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated, idempotent block and unblock operations keyed by opaque profile public ids. */
@RestController
@RequestMapping("/api/v1/blocks")
class BlockController {

	private final BlockService blocks;

	BlockController(BlockService blocks) {
		this.blocks = blocks;
	}

	@PutMapping("/{profilePublicId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void block(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID profilePublicId) {
		blocks.block(jwt.getSubject(), profilePublicId);
	}

	@DeleteMapping("/{profilePublicId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void unblock(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID profilePublicId) {
		blocks.unblock(jwt.getSubject(), profilePublicId);
	}
}
