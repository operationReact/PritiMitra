package com.pritimitra.backend.onboarding;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OnboardingProfileRepository extends JpaRepository<OnboardingProfile, UUID> {
}
