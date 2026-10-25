package com.pritimitra.backend.onboarding;

import com.pritimitra.backend.common.ApiException;
import com.pritimitra.backend.config.LegalProperties;
import com.pritimitra.backend.identity.AccountStatus;
import com.pritimitra.backend.identity.AppUser;
import com.pritimitra.backend.identity.CurrentUserService;
import com.pritimitra.backend.identity.EmailVerificationChecker;
import com.pritimitra.backend.onboarding.OnboardingDtos.ConsentItem;
import com.pritimitra.backend.onboarding.OnboardingDtos.MeResponse;
import com.pritimitra.backend.onboarding.OnboardingDtos.OnboardingStateResponse;
import com.pritimitra.backend.onboarding.OnboardingDtos.OwnProfileResponse;
import com.pritimitra.backend.onboarding.OnboardingDtos.ProfileRequest;
import com.pritimitra.backend.onboarding.OnboardingDtos.ProfileResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the account activation rules. A user becomes ACTIVE only after: email verified in Cognito, date of birth
 * verified as 18+ on the server, current Terms and Privacy consent recorded, a display name, and at least one
 * connection goal. Discovery preferences are optional at activation and are validated when supplied.
 */
@Service
class OnboardingService {

	private final CurrentUserService currentUsers;
	private final EmailVerificationChecker emailVerification;
	private final OnboardingProfileRepository profiles;
	private final ConsentRecordRepository consents;
	private final LegalProperties legal;
	private final Clock clock;

	OnboardingService(CurrentUserService currentUsers, EmailVerificationChecker emailVerification,
			OnboardingProfileRepository profiles, ConsentRecordRepository consents, LegalProperties legal,
			Clock clock) {
		this.currentUsers = currentUsers;
		this.emailVerification = emailVerification;
		this.profiles = profiles;
		this.consents = consents;
		this.legal = legal;
		this.clock = clock;
	}

	@Transactional
	MeResponse getMe(String cognitoSub, String accessToken) {
		AppUser user = resolve(cognitoSub, accessToken);
		String displayName = profiles.findById(user.getId()).map(OnboardingProfile::getDisplayName).orElse(null);
		return new MeResponse(user.getId(), user.getStatus(), displayName);
	}

	@Transactional
	OnboardingStateResponse getState(String cognitoSub, String accessToken) {
		return stateOf(resolve(cognitoSub, accessToken));
	}

	@Transactional
	ProfileResponse getProfile(String cognitoSub, String accessToken) {
		AppUser user = resolve(cognitoSub, accessToken);
		return profiles.findById(user.getId())
				.map(p -> new ProfileResponse(p.getDisplayName(), p.getBio(), p.getProfilePrompt(),
						new HashSet<>(p.getGoals())))
				.orElseGet(() -> new ProfileResponse(null, null, null, Set.of()));
	}

	/** The owner's full profile. Available only to ACTIVE accounts; the default-deny filter enforces this first. */
	@Transactional
	OwnProfileResponse getOwnProfile(String cognitoSub, String accessToken) {
		AppUser user = requireActive(cognitoSub, accessToken);
		return toOwnProfile(profiles.findById(user.getId())
				.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "PROFILE_INCOMPLETE",
						"Complete onboarding to view your profile")));
	}

	/**
	 * Updates the editable profile of an ACTIVE account. Activation rules still apply on every edit, so a display
	 * name and at least one connection goal must remain set.
	 */
	@Transactional
	OwnProfileResponse updateOwnProfile(String cognitoSub, String accessToken, ProfileRequest request) {
		AppUser user = requireActive(cognitoSub, accessToken);
		if (request.displayName() == null || request.displayName().isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "DISPLAY_NAME_REQUIRED", "A display name is required");
		}
		if (request.goals() == null || request.goals().isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CONNECTION_GOAL_REQUIRED",
					"Choose at least one connection goal");
		}
		updateProfile(cognitoSub, accessToken, request);
		return toOwnProfile(profiles.findById(user.getId()).orElseThrow());
	}

	/** Opt in or out of discovery. Opting in requires a completed profile; opting out is always allowed. */
	@Transactional
	OwnProfileResponse setDiscoverable(String cognitoSub, String accessToken, boolean discoverable) {
		AppUser user = requireActive(cognitoSub, accessToken);
		OnboardingProfile profile = profiles.findById(user.getId())
				.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "PROFILE_INCOMPLETE",
						"Complete onboarding before changing discovery visibility"));
		if (discoverable && !profile.isCompleted()) {
			throw new ApiException(HttpStatus.CONFLICT, "PROFILE_INCOMPLETE",
					"Complete onboarding before changing discovery visibility");
		}
		profile.setDiscoveryVisible(discoverable, Instant.now(clock));
		profiles.save(profile);
		return toOwnProfile(profile);
	}

	/**
	 * Records the date of birth. Returns {@code false} when the user is under the minimum age; the account is then
	 * marked INELIGIBLE and the date of birth is not stored. The caller maps {@code false} to HTTP 403 without
	 * throwing, so the status change is committed.
	 */
	@Transactional
	boolean verifyAge(String cognitoSub, String accessToken, LocalDate dateOfBirth) {
		AppUser user = requireAccessible(cognitoSub, accessToken);
		Instant now = Instant.now(clock);
		LocalDate today = LocalDate.now(clock);

		Optional<OnboardingProfile> existing = profiles.findById(user.getId());
		if (existing.isPresent() && existing.get().getDateOfBirth() != null) {
			if (existing.get().getDateOfBirth().equals(dateOfBirth)) {
				return true;
			}
			throw new ApiException(HttpStatus.CONFLICT, "DATE_OF_BIRTH_LOCKED",
					"Date of birth has already been verified and cannot be changed");
		}
		if (user.getStatus() == AccountStatus.INELIGIBLE) {
			return false;
		}
		if (dateOfBirth.isAfter(today) || dateOfBirth.isBefore(today.minusYears(AgePolicy.MAX_PLAUSIBLE_AGE))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_OF_BIRTH", "Date of birth is not valid");
		}
		if (!AgePolicy.isEligible(dateOfBirth, today)) {
			user.markIneligible(now);
			return false;
		}
		OnboardingProfile profile = existing.orElseGet(() -> OnboardingProfile.create(user.getId(), now));
		profile.recordDateOfBirth(dateOfBirth, now);
		profiles.save(profile);
		return true;
	}

	@Transactional
	OnboardingStateResponse recordConsents(String cognitoSub, String accessToken, List<ConsentItem> items) {
		AppUser user = requireOnboardable(cognitoSub, accessToken);
		requireAgeVerified(user);
		Instant now = Instant.now(clock);

		for (ConsentItem item : items) {
			if (!item.accepted()) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "CONSENT_NOT_ACCEPTED",
						"Terms and privacy consent must be accepted to continue");
			}
			String currentVersion = currentVersionOf(item.type());
			if (!currentVersion.equals(item.policyVersion())) {
				throw new ApiException(HttpStatus.CONFLICT, "POLICY_VERSION_MISMATCH",
						"The submitted policy version is not current. Refresh the policy and accept again.");
			}
			if (!consents.existsByUserIdAndConsentTypeAndPolicyVersionAndAcceptedTrue(user.getId(), item.type(),
					currentVersion)) {
				consents.save(new ConsentRecord(user.getId(), item.type(), currentVersion, true, now));
			}
		}
		return stateOf(user);
	}

	@Transactional
	OnboardingStateResponse updateProfile(String cognitoSub, String accessToken, ProfileRequest request) {
		AppUser user = requireOnboardable(cognitoSub, accessToken);
		requireAgeVerified(user);
		requireRequiredConsents(user);

		Integer minAge = request.discoveryMinAge();
		Integer maxAge = request.discoveryMaxAge();
		requireAllOrNoneDiscoveryPreferences(request);
		if (minAge != null && maxAge != null && minAge > maxAge) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AGE_RANGE",
					"Minimum discovery age cannot be greater than maximum");
		}
		Set<DiscoveryInterest> discoveryInterests = normalizeDiscoveryInterests(request.discoveryInterestedIn());

		OnboardingProfile profile = profiles.findById(user.getId())
				.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "AGE_VERIFICATION_REQUIRED",
						"Verify your age before updating your profile"));
		Set<ConnectionGoal> goals = request.goals() == null ? Set.of() : request.goals();
		Set<String> interests = normalizeInterests(request.interests());
		profile.applyProfile(request, goals, interests, discoveryInterests, Instant.now(clock));
		profiles.save(profile);
		return stateOf(user);
	}

	@Transactional
	OnboardingStateResponse complete(String cognitoSub, String accessToken) {
		AppUser user = requireOnboardable(cognitoSub, accessToken);
		if (user.getStatus() == AccountStatus.ACTIVE) {
			return stateOf(user);
		}
		requireAgeVerified(user);
		requireRequiredConsents(user);

		OnboardingProfile profile = profiles.findById(user.getId())
				.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "PROFILE_INCOMPLETE",
						"Required profile details are missing"));
		List<String> missing = missingActivationFields(profile);
		if (!missing.isEmpty()) {
			throw new ApiException(HttpStatus.CONFLICT, "PROFILE_INCOMPLETE",
					"Required profile details are missing: " + String.join(", ", missing));
		}
		Instant now = Instant.now(clock);
		profile.markCompleted(now);
		profiles.save(profile);
		user.activate(now);
		return stateOf(user);
	}

	/**
	 * Resolves the account for a verified Cognito subject and promotes it from PENDING_EMAIL_VERIFICATION once
	 * Cognito confirms the email address. Suspended and ineligible accounts are returned unchanged.
	 */
	private AppUser resolve(String cognitoSub, String accessToken) {
		AppUser user = currentUsers.getOrProvision(cognitoSub);
		if (user.getStatus() == AccountStatus.PENDING_EMAIL_VERIFICATION
				&& emailVerification.isEmailVerified(accessToken)) {
			user.markEmailVerified(Instant.now(clock));
		}
		return user;
	}

	private OnboardingStateResponse stateOf(AppUser user) {
		Optional<OnboardingProfile> profile = profiles.findById(user.getId());
		boolean ageVerified = profile.map(p -> p.getDateOfBirth() != null).orElse(false);
		boolean consentsAccepted = requiredConsentsAccepted(user);
		boolean discoveryConfigured = profile.map(this::discoveryPreferencesConfigured).orElse(false);
		List<String> missing = profile.map(this::missingActivationFields)
				.orElse(List.of("displayName", "connectionGoals"));
		return new OnboardingStateResponse(user.getStatus(), ageVerified, consentsAccepted, discoveryConfigured,
				missing);
	}

	private List<String> missingActivationFields(OnboardingProfile profile) {
		List<String> missing = new ArrayList<>();
		if (profile.getDisplayName() == null) {
			missing.add("displayName");
		}
		if (profile.getGoals().isEmpty()) {
			missing.add("connectionGoals");
		}
		return missing;
	}

	private boolean discoveryPreferencesConfigured(OnboardingProfile profile) {
		return !profile.getDiscoveryInterests().isEmpty() && profile.getDiscoveryMinAge() != null
				&& profile.getDiscoveryMaxAge() != null && profile.getDiscoveryMaxDistanceKm() != null;
	}

	private void requireAllOrNoneDiscoveryPreferences(ProfileRequest request) {
		int provided = 0;
		if (request.discoveryInterestedIn() != null && !request.discoveryInterestedIn().isEmpty()) {
			provided++;
		}
		if (request.discoveryMinAge() != null) {
			provided++;
		}
		if (request.discoveryMaxAge() != null) {
			provided++;
		}
		if (request.discoveryMaxDistanceKm() != null) {
			provided++;
		}
		if (provided != 0 && provided != 4) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INCOMPLETE_DISCOVERY_PREFERENCES",
					"Discovery preferences must be set together: interested in, age range and distance");
		}
	}

	private boolean requiredConsentsAccepted(AppUser user) {
		return consents.existsByUserIdAndConsentTypeAndPolicyVersionAndAcceptedTrue(user.getId(),
				ConsentType.TERMS_OF_SERVICE, legal.termsVersion())
				&& consents.existsByUserIdAndConsentTypeAndPolicyVersionAndAcceptedTrue(user.getId(),
						ConsentType.PRIVACY_NOTICE, legal.privacyVersion());
	}

	private String currentVersionOf(ConsentType type) {
		return switch (type) {
			case TERMS_OF_SERVICE -> legal.termsVersion();
			case PRIVACY_NOTICE -> legal.privacyVersion();
			case COMMUNITY_NICKNAME -> throw new ApiException(HttpStatus.BAD_REQUEST, "CONSENT_TYPE_UNSUPPORTED",
					"This consent type is not collected yet");
		};
	}

	private AppUser requireOnboardable(String cognitoSub, String accessToken) {
		AppUser user = requireAccessible(cognitoSub, accessToken);
		if (user.getStatus() == AccountStatus.INELIGIBLE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "AGE_INELIGIBLE",
					"PritiMitra is available to adults aged 18 or older");
		}
		return user;
	}

	private AppUser requireAccessible(String cognitoSub, String accessToken) {
		AppUser user = resolve(cognitoSub, accessToken);
		if (user.getStatus() == AccountStatus.SUSPENDED) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED", "This account is not available");
		}
		if (user.getStatus() == AccountStatus.PENDING_EMAIL_VERIFICATION) {
			throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
					"Verify your email address to continue");
		}
		return user;
	}

	private AppUser requireActive(String cognitoSub, String accessToken) {
		AppUser user = requireAccessible(cognitoSub, accessToken);
		if (user.getStatus() == AccountStatus.INELIGIBLE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "AGE_INELIGIBLE",
					"PritiMitra is available to adults aged 18 or older");
		}
		if (user.getStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ONBOARDING_INCOMPLETE",
					"Complete onboarding to continue");
		}
		return user;
	}

	private static OwnProfileResponse toOwnProfile(OnboardingProfile p) {
		return new OwnProfileResponse(p.getPublicId(), p.getDisplayName(), p.getBio(), p.getProfilePrompt(),
				p.getGenderIdentity(), p.isGenderIdentityVisible(), p.getPronouns(), p.isPronounsVisible(),
				p.getSexualOrientation(), p.isSexualOrientationVisible(), new LinkedHashSet<>(p.getGoals()),
				new LinkedHashSet<>(p.getInterests()), new LinkedHashSet<>(p.getDiscoveryInterests()),
				p.getDiscoveryMinAge(), p.getDiscoveryMaxAge(), p.getDiscoveryMaxDistanceKm(),
				p.isDiscoveryVisible());
	}

	private void requireAgeVerified(AppUser user) {
		boolean verified = profiles.findById(user.getId()).map(p -> p.getDateOfBirth() != null).orElse(false);
		if (!verified) {
			throw new ApiException(HttpStatus.CONFLICT, "AGE_VERIFICATION_REQUIRED",
					"Verify your age before continuing");
		}
	}

	private void requireRequiredConsents(AppUser user) {
		if (!requiredConsentsAccepted(user)) {
			throw new ApiException(HttpStatus.CONFLICT, "CONSENT_REQUIRED",
					"Accept the current terms and privacy notice before continuing");
		}
	}

	private static Set<String> normalizeInterests(Set<String> raw) {
		Set<String> normalized = new LinkedHashSet<>();
		if (raw == null) {
			return normalized;
		}
		for (String interest : raw) {
			String trimmed = interest == null ? "" : interest.trim();
			if (trimmed.isEmpty()) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INTEREST", "Interests cannot be blank");
			}
			normalized.add(trimmed);
		}
		return normalized;
	}

	/** Empty or omitted preferences are allowed. ALL_IDENTITIES cannot be combined with specific identities. */
	private static Set<DiscoveryInterest> normalizeDiscoveryInterests(Set<DiscoveryInterest> raw) {
		Set<DiscoveryInterest> normalized = raw == null ? new LinkedHashSet<>() : new LinkedHashSet<>(raw);
		if (normalized.contains(DiscoveryInterest.ALL_IDENTITIES) && normalized.size() > 1) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DISCOVERY_INTEREST",
					"ALL_IDENTITIES cannot be combined with specific identities");
		}
		return normalized;
	}
}
