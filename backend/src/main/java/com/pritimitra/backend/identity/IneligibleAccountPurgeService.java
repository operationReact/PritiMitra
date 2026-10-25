package com.pritimitra.backend.identity;

import com.pritimitra.backend.config.RetentionProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes INELIGIBLE accounts after the retention window. The Cognito identity is deleted first, and the application
 * row (with its cascaded auth and onboarding rows) only afterwards. Both steps are idempotent, so a failed run is
 * completed by the next one. INELIGIBLE accounts never hold consent records, because consent requires age
 * verification, so the append-only consent trigger does not block the cascade.
 */
@Service
public class IneligibleAccountPurgeService {

	private final AppUserRepository users;
	private final CognitoUserAdministration cognito;
	private final RetentionProperties retention;
	private final Clock clock;

	public IneligibleAccountPurgeService(AppUserRepository users, CognitoUserAdministration cognito,
			RetentionProperties retention, Clock clock) {
		this.users = users;
		this.cognito = cognito;
		this.retention = retention;
		this.clock = clock;
	}

	@Transactional
	public int purgeExpiredIneligibleAccounts() {
		Instant cutoff = Instant.now(clock).minus(Duration.ofDays(retention.ineligibleAccountDays()));
		List<AppUser> expired = users.findByStatusAndIneligibleAtBefore(AccountStatus.INELIGIBLE, cutoff);
		for (AppUser user : expired) {
			cognito.deleteUser(user.getCognitoSub());
			users.delete(user);
		}
		return expired.size();
	}
}
