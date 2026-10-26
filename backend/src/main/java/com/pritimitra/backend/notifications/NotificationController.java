package com.pritimitra.backend.notifications;

import com.pritimitra.backend.notifications.NotificationDtos.NotificationPage;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated, owner-only in-app notifications. */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

	private final NotificationService notifications;

	NotificationController(NotificationService notifications) {
		this.notifications = notifications;
	}

	@GetMapping
	NotificationPage list(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam(name = "cursor", required = false) String cursor,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		return notifications.list(jwt.getSubject(), cursor, size);
	}

	@PutMapping("/{notificationPublicId}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID notificationPublicId) {
		notifications.markRead(jwt.getSubject(), notificationPublicId);
	}

	@PutMapping("/read-all")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void markAllRead(@AuthenticationPrincipal Jwt jwt) {
		notifications.markAllRead(jwt.getSubject());
	}
}
