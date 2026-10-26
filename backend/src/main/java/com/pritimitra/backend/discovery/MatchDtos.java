package com.pritimitra.backend.discovery;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Match DTOs. Carry only opaque match identifiers and the other participant's public-safe profile fields. */
public final class MatchDtos {

	private MatchDtos() {
	}

	/** Result of a like with respect to matching. {@code CREATED} means a match now exists and was newly formed. */
	public enum MatchState {
		NONE,
		CREATED,
		EXISTING
	}

	/**
	 * A match visible to one of its participants. {@code displayName} is null when the other participant is no
	 * longer available, for example after deactivation.
	 */
	public record MatchSummary(
			UUID matchPublicId,
			String status,
			String displayName,
			boolean available,
			Instant matchedAt) {
	}

	/** One page of active matches. {@code nextCursor} is null when no further results exist. */
	public record MatchPage(List<MatchSummary> items, String nextCursor) {
	}
}
