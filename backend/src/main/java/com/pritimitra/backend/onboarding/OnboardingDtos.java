package com.pritimitra.backend.onboarding;

import com.pritimitra.backend.identity.AccountStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class OnboardingDtos {

	private OnboardingDtos() {
	}

	public record AgeVerificationRequest(@NotNull LocalDate dateOfBirth) {
	}

	public record ConsentRequest(@NotEmpty List<@Valid ConsentItem> consents) {
	}

	public record ConsentItem(@NotNull ConsentType type, @NotBlank @Size(max = 20) String policyVersion,
			boolean accepted) {
	}

	/**
	 * Full replacement of the editable profile. Visibility flags default to false (private) when omitted.
	 * Goals are required only at activation. Discovery preferences are optional, but must be provided together.
	 */
	public record ProfileRequest(
			@Size(max = 60) String displayName,
			@Size(max = 500) String bio,
			@Size(max = 200) String profilePrompt,
			@Size(max = 40) String genderIdentity,
			Boolean genderIdentityVisible,
			@Size(max = 40) String pronouns,
			Boolean pronounsVisible,
			@Size(max = 40) String sexualOrientation,
			Boolean sexualOrientationVisible,
			@Size(max = 4) Set<ConnectionGoal> goals,
			@Size(max = 20) Set<@Size(min = 1, max = 40) String> interests,
			@Size(max = 4) Set<DiscoveryInterest> discoveryInterestedIn,
			@Min(AgePolicy.MINIMUM_AGE) @Max(99) Integer discoveryMinAge,
			@Min(AgePolicy.MINIMUM_AGE) @Max(99) Integer discoveryMaxAge,
			@Min(1) @Max(500) Integer discoveryMaxDistanceKm) {
	}

	public record OnboardingStateResponse(AccountStatus status, boolean ageVerified,
			boolean requiredConsentsAccepted, boolean discoveryPreferencesConfigured, List<String> missingFields) {
	}

	public record MeResponse(UUID id, AccountStatus status, String displayName) {
	}
}
