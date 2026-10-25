package com.pritimitra.backend.onboarding;

/** Identities a user is interested in discovering. Codes are validated on the server; unknown codes are rejected. */
public enum DiscoveryInterest {
	MEN,
	WOMEN,
	NON_BINARY,
	/** Mutually exclusive with the specific identities. */
	ALL_IDENTITIES
}
