package com.pritimitra.backend.identity;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

/**
 * Administrative operations on Cognito users, used by the retention purge. The user is addressed by its immutable
 * {@code sub}, which Cognito accepts as the username for user pools that sign in with email.
 */
@Component
public class CognitoUserAdministration {

	private final CognitoIdentityProviderClient cognito;
	private final CognitoProperties properties;

	CognitoUserAdministration(CognitoIdentityProviderClient cognito, CognitoProperties properties) {
		this.cognito = cognito;
		this.properties = properties;
	}

	/** Deletes the Cognito identity. A user that no longer exists is treated as already deleted. */
	public void deleteUser(String cognitoSub) {
		if (properties.userPoolId() == null || properties.userPoolId().isBlank()) {
			throw new IllegalStateException("COGNITO_USER_POOL_ID must be configured to delete Cognito users");
		}
		try {
			cognito.adminDeleteUser(AdminDeleteUserRequest.builder()
					.userPoolId(properties.userPoolId())
					.username(cognitoSub)
					.build());
		} catch (UserNotFoundException ignored) {
			// Already deleted in a previous purge run; the purge is idempotent.
		}
	}
}
