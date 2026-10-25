package com.pritimitra.backend.onboarding;

import java.time.LocalDate;
import java.time.Period;

/** PritiMitra is restricted to adults. Eligibility is decided here on the server, never by the client. */
final class AgePolicy {

	static final int MINIMUM_AGE = 18;

	/** Oldest plausible date of birth; anything earlier is rejected as invalid input. */
	static final int MAX_PLAUSIBLE_AGE = 120;

	private AgePolicy() {
	}

	static boolean isEligible(LocalDate dateOfBirth, LocalDate today) {
		return Period.between(dateOfBirth, today).getYears() >= MINIMUM_AGE;
	}
}
