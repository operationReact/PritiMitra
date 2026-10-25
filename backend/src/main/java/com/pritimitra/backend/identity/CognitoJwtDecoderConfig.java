package com.pritimitra.backend.identity;

import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
class CognitoJwtDecoderConfig {

	/**
	 * Builds a JWKS-backed decoder. Without a configured issuer and client list every token is rejected (fail closed).
	 * Keys are fetched lazily by Nimbus, so startup does not require network access to Cognito.
	 */
	@Bean
	JwtDecoder jwtDecoder(CognitoProperties properties) {
		if (!StringUtils.hasText(properties.issuer()) || properties.clientIds().isEmpty()) {
			return token -> {
				throw new BadJwtException("Cognito is not configured for this environment");
			};
		}
		String issuer = properties.issuer().endsWith("/")
				? properties.issuer().substring(0, properties.issuer().length() - 1)
				: properties.issuer();
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json").build();
		decoder.setJwtValidator(CognitoJwtValidators.accessToken(issuer, Set.copyOf(properties.clientIds())));
		return decoder;
	}
}
