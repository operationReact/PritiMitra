package com.pritimitra.backend.safety;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ModerationDtos {
    private ModerationDtos() {}

    public record ReportItem(
            UUID reportId,
            String targetType,
            UUID targetPublicId,
            String category,
            String details,
            String status,
            OffsetDateTime createdAt,
            UUID reportedProfilePublicId,
            String reportedDisplayName,
            String evidence) {}

    public record ReportPage(List<ReportItem> items, String nextCursor, boolean hasMore) {}

    /** decision is DISMISS, HIDE_CONTENT (community posts only), or SUSPEND_ACCOUNT. */
    public record ReviewRequest(String decision, String note) {}

    public record ReviewReceipt(UUID reportId, String status, String action, OffsetDateTime reviewedAt) {}
}
