package com.pritimitra.backend.safety;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ModerationAuditDtos {
    private ModerationAuditDtos() {}

    public record AuditHistoryItem(
            UUID eventId,
            String actorReference,
            String eventType,
            UUID reportPublicId,
            String targetType,
            UUID targetPublicId,
            String note,
            OffsetDateTime createdAt) {}

    public record AuditHistoryPage(
            List<AuditHistoryItem> items,
            String nextCursor,
            OffsetDateTime from,
            OffsetDateTime to) {}
}
