package com.pritimitra.backend.identity;

public enum AccountStatus {
	/** Provisioned from a valid Cognito token, but the Cognito email address is not yet verified. */
	PENDING_EMAIL_VERIFICATION,
	/** Email verified; age, consent or required onboarding steps are not all complete. */
	ONBOARDING_INCOMPLETE,
	/** Eligible and onboarded. The only status that unlocks protected product APIs. */
	ACTIVE,
	/** Blocked by an administrator or trust-and-safety process. Protected APIs are unavailable. */
	SUSPENDED,
	/** Declared under 18. Terminal. The date of birth is not stored for these accounts. */
	INELIGIBLE,
	/** User requested deletion; protected product APIs are disabled while cleanup retries. */
	DELETION_PENDING
}
