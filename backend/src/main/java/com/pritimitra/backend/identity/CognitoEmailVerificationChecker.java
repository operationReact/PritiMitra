package com.pritimitra.backend.identity;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserResponse;

@Component
class CognitoEmailVerificationChecker implements EmailVerificationChecker {

	private final CognitoIdentityProviderClient cognito;

	CognitoEmailVerificationChecker(CognitoIdentityProviderClient cognito) {
		this.cognito = cognito;
	}

	@Override
	public boolean isEmailVerified(String accessToken) {
		GetUserResponse user = cognito.getUser(GetUserRequest.builder().accessToken(accessToken).build());
		return user.userAttributes().stream()
				.filter(attribute -> "email_verified".equals(attribute.name()))
				.map(AttributeType::value)
				.anyMatch("true"::equalsIgnoreCase);
	}
}
