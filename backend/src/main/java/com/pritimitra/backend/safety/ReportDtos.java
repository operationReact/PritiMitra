package com.pritimitra.backend.safety;

import java.util.UUID;

public final class ReportDtos {

	private ReportDtos() {
	}

	/**
	 * targetType is PROFILE, MESSAGE or COMMUNITY_POST. category is one of HARASSMENT, IMPERSONATION, UNWANTED_CONTENT, SPAM, OTHER.
	 * Both are strings so invalid values produce a consistent INVALID_REPORT error instead of a binding failure.
	 */
	public record ReportRequest(String targetType, UUID targetId, String category, String details) {
	}

	/** Returned only to the reporter. Reports are never exposed through any other DTO. */
	public record ReportReceipt(UUID reportId, String status) {
	}
}
