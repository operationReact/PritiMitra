package com.pritimitra.backend.notifications;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class NotificationDtos {

	private NotificationDtos() {
	}

	public record NotificationSummary(UUID notificationId, String kind, UUID conversationId, boolean read,
			Instant createdAt) {
	}

	public record NotificationPage(List<NotificationSummary> items, String nextCursor, long unreadCount) {
	}
}
