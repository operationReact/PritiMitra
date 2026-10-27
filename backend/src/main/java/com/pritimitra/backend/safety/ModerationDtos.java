package com.pritimitra.backend.safety;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ModerationDtos {
    private ModerationDtos() {}

    public record ReportQueueItem(
            UUID reportId,
            String targetType,
            UUID targetPublicId,
            UUID reporterProfilePublicId,
            UUID reportedProfilePublicId,
            String category,
            String details,
            String targetPreview,
            String status,
            OffsetDateTime createdAt,
            String reviewDecision,
            String reviewNote,
            OffsetDateTime reviewedAt,
            String reviewerDisplayName) {}

    public record ReportQueuePage(List<ReportQueueItem> items, String nextCursor) {}

    public record ReviewRequest(String decision, String note) {}

    public record ReviewReceipt(
            UUID reportId,
            String status,
            String decision,
            OffsetDateTime reviewedAt) {}
}
