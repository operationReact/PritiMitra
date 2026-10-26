package com.pritimitra.backend.common;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Opaque, URL-safe pagination cursors built from public UUIDs. Shared by discovery and messaging. */
public final class PublicIdCursor {

	private PublicIdCursor() {
	}

	/** Returns null for a blank cursor; malformed cursors are rejected with 400 {@code INVALID_CURSOR}. */
	public static UUID decode(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}
		try {
			String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			return UUID.fromString(decoded);
		} catch (IllegalArgumentException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "Pagination cursor is invalid");
		}
	}

	public static String encode(UUID publicId) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(publicId.toString().getBytes(StandardCharsets.UTF_8));
	}
}
