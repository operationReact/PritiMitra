package com.pritimitra.backend.identity;

import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

	private final AppUserRepository users;
	private final Clock clock;

	public CurrentUserService(AppUserRepository users, Clock clock) {
		this.users = users;
		this.clock = clock;
	}

	/** Returns the account for a verified Cognito subject, creating it on first authenticated use. */
	public AppUser getOrProvision(String cognitoSub) {
		return users.findByCognitoSub(cognitoSub).orElseGet(() -> provision(cognitoSub));
	}

	public boolean isEligible(String cognitoSub) {
		return users.findByCognitoSub(cognitoSub)
				.map(user -> user.getStatus() == AccountStatus.ACTIVE)
				.orElse(false);
	}

	private AppUser provision(String cognitoSub) {
		try {
			return users.saveAndFlush(new AppUser(cognitoSub, Instant.now(clock)));
		}
		catch (DataIntegrityViolationException concurrentInsert) {
			// Another request created the same subject first; the unique cognito_sub constraint is the arbiter.
			return users.findByCognitoSub(cognitoSub).orElseThrow(() -> concurrentInsert);
		}
	}
}
