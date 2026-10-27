package com.pritimitra.backend.discovery;

import java.util.List;
import java.util.UUID;

/** Privacy-safe discovery DTOs. Never carry email, Cognito subject, date of birth, consent or moderation data. */
public final class DiscoveryDtos {

	private DiscoveryDtos() {
	}

	/** One page of discoverable profiles. {@code nextCursor} is null when no further results exist. */
	public record DiscoveryPage(List<DiscoveryCard> items, String nextCursor) {
	}

	/** Fields a discoverable profile exposes. Optional identity fields are null unless the owner made them visible. */
	public record DiscoveryCard(
			UUID publicId,
			String displayName,
			String bio,
			String profilePrompt,
			String genderIdentity,
			String pronouns,
			String sexualOrientation,
			List<String> goals,
			List<String> interests) {
	}

	public record LikeStatus(boolean liked) {
	}

	/**
	 * One page of the viewer's outgoing likes. {@code nextCursor} is null when no further results exist.
	 * Items whose profile is no longer discoverable keep their identifier so the viewer can still remove the like,
	 * but expose no display name.
	 */
	public record LikedProfilePage(List<LikedProfile> items, String nextCursor) {
	}

	public record LikedProfile(UUID publicId, String displayName, boolean available) {
	}
}
