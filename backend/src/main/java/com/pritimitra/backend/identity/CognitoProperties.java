package com.pritimitra.backend.identity;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cognito settings, supplied per environment. Empty values disable token acceptance (fail closed).
 */
@ConfigurationProperties("pritimitra.auth.cognito")
public record CognitoProperties(String issuer, String region, String userPoolId, List<String> clientIds) {

	public CognitoProperties {
		clientIds = clientIds == null ? List.of()
				: clientIds.stream().filter(id -> id != null && !id.isBlank()).map(String::trim).toList();
	}
}
