package com.pritimitra.backend.photos;

import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Owner-only profile photo endpoints. Delivery is private: no public URLs are issued. */
@RestController
@RequestMapping("/api/v1/profile/photos")
class PhotoController {

	private final PhotoService photos;

	PhotoController(PhotoService photos) {
		this.photos = photos;
	}

	@GetMapping
	List<PhotoResponse> list(@AuthenticationPrincipal Jwt jwt) {
		return photos.list(jwt.getSubject());
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<PhotoResponse> upload(@AuthenticationPrincipal Jwt jwt,
			@RequestParam("file") MultipartFile file) throws java.io.IOException {
		PhotoResponse created = photos.upload(jwt.getSubject(), file);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@GetMapping("/{photoId}/content")
	ResponseEntity<byte[]> content(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID photoId) {
		PhotoService.PhotoContent content = photos.content(jwt.getSubject(), photoId);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore().cachePrivate())
				.header(HttpHeaders.CONTENT_TYPE, content.contentType())
				.header("X-Content-Type-Options", "nosniff")
				.body(content.bytes());
	}

	@DeleteMapping("/{photoId}")
	ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID photoId) {
		photos.delete(jwt.getSubject(), photoId);
		return ResponseEntity.noContent().build();
	}
}
