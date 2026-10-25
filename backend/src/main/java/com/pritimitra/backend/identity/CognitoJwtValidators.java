package com.pritimitra.backend.identity;

import java.util.Set;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;

/**
 * Validators for Cognito access tokens. Cognito access tokens carry {@code client_id} instead of {@code aud},
 * and {@code token_use} distinguishes them from ID tokens, so both must be checked explicitly.
 */
final class CognitoJwtValidators {

	private CognitoJwtValidators() {
	}

	static OAuth2TokenValidator<Jwt> accessToken(String issuer, Set<String> allowedClientIds) {
		return new DelegatingOAuth2TokenValidator<>(
				new JwtIssuerValidator(issuer),
				new JwtTimestampValidator(),
				new JwtClaimValidator<String>("token_use", "access"::equals),
				new JwtClaimValidator<String>("client_id", clientId -> clientId != null && allowedClientIds.contains(clientId)),
				new JwtClaimValidator<String>("sub", subject -> subject != null && !subject.isBlank()));
	}
}
