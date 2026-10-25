package com.pritimitra.backend.onboarding;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {

	boolean existsByUserIdAndConsentTypeAndPolicyVersionAndAcceptedTrue(UUID userId, ConsentType consentType,
			String policyVersion);
}
