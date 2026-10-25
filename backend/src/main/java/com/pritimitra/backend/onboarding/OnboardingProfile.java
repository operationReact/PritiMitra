package com.pritimitra.backend.onboarding;

import com.pritimitra.backend.onboarding.OnboardingDtos.ProfileRequest;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "onboarding_profile")
class OnboardingProfile {

	@Id
	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "display_name", length = 60)
	private String displayName;

	@Column(name = "date_of_birth")
	private LocalDate dateOfBirth;

	@Column(name = "age_verified_at")
	private Instant ageVerifiedAt;

	@Column(length = 500)
	private String bio;

	@Column(name = "profile_prompt", length = 200)
	private String profilePrompt;

	@Column(name = "gender_identity", length = 40)
	private String genderIdentity;

	@Column(length = 40)
	private String pronouns;

	/** Identity visibility defaults to private. */
	@Column(name = "gender_identity_visible", nullable = false)
	private boolean genderIdentityVisible;

	@Column(name = "pronouns_visible", nullable = false)
	private boolean pronounsVisible;

	@Column(name = "sexual_orientation", length = 40)
	private String sexualOrientation;

	@Column(name = "sexual_orientation_visible", nullable = false)
	private boolean sexualOrientationVisible;

	@Column(name = "discovery_min_age")
	private Integer discoveryMinAge;

	@Column(name = "discovery_max_age")
	private Integer discoveryMaxAge;

	@Column(name = "discovery_max_distance_km")
	private Integer discoveryMaxDistanceKm;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_connection_goal", joinColumns = @JoinColumn(name = "user_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "goal", nullable = false, length = 32)
	private Set<ConnectionGoal> goals = new HashSet<>();

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "discovery_interest", joinColumns = @JoinColumn(name = "user_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "interest", nullable = false, length = 20)
	private Set<DiscoveryInterest> discoveryInterests = new HashSet<>();

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_interest", joinColumns = @JoinColumn(name = "user_id"))
	@Column(name = "interest", nullable = false, length = 40)
	private Set<String> interests = new HashSet<>();

	protected OnboardingProfile() {
	}

	private OnboardingProfile(UUID userId, Instant now) {
		this.userId = userId;
		this.updatedAt = now;
	}

	static OnboardingProfile create(UUID userId, Instant now) {
		return new OnboardingProfile(userId, now);
	}

	void recordDateOfBirth(LocalDate dateOfBirth, Instant now) {
		this.dateOfBirth = dateOfBirth;
		this.ageVerifiedAt = now;
		this.updatedAt = now;
	}

	/** Replaces the editable profile. Callers must have already checked age and consent prerequisites. */
	void applyProfile(ProfileRequest request, Set<ConnectionGoal> newGoals, Set<String> newInterests,
			Set<DiscoveryInterest> newDiscoveryInterests, Instant now) {
		this.displayName = clean(request.displayName());
		this.bio = clean(request.bio());
		this.profilePrompt = clean(request.profilePrompt());
		this.genderIdentity = clean(request.genderIdentity());
		this.genderIdentityVisible = Boolean.TRUE.equals(request.genderIdentityVisible());
		this.pronouns = clean(request.pronouns());
		this.pronounsVisible = Boolean.TRUE.equals(request.pronounsVisible());
		this.sexualOrientation = clean(request.sexualOrientation());
		this.sexualOrientationVisible = Boolean.TRUE.equals(request.sexualOrientationVisible());
		this.discoveryInterests = new LinkedHashSet<>(newDiscoveryInterests);
		this.discoveryMinAge = request.discoveryMinAge();
		this.discoveryMaxAge = request.discoveryMaxAge();
		this.discoveryMaxDistanceKm = request.discoveryMaxDistanceKm();
		this.goals = new LinkedHashSet<>(newGoals);
		this.interests = new LinkedHashSet<>(newInterests);
		this.updatedAt = now;
	}

	void markCompleted(Instant now) {
		this.completedAt = now;
		this.updatedAt = now;
	}

	UUID getUserId() {
		return userId;
	}

	LocalDate getDateOfBirth() {
		return dateOfBirth;
	}

	String getDisplayName() {
		return displayName;
	}

	String getBio() {
		return bio;
	}

	String getProfilePrompt() {
		return profilePrompt;
	}

	Integer getDiscoveryMinAge() {
		return discoveryMinAge;
	}

	Integer getDiscoveryMaxAge() {
		return discoveryMaxAge;
	}

	Integer getDiscoveryMaxDistanceKm() {
		return discoveryMaxDistanceKm;
	}

	Set<DiscoveryInterest> getDiscoveryInterests() {
		return discoveryInterests;
	}

	Set<ConnectionGoal> getGoals() {
		return goals;
	}

	Set<String> getInterests() {
		return interests;
	}

	private static String clean(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
