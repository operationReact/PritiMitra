package com.pritimitra.backend.identity;

import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/** Allows a request only when the authenticated Cognito subject maps to an ACTIVE PritiMitra account. */
@Component
class EligibleUserAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

	private final CurrentUserService currentUsers;

	EligibleUserAuthorizationManager(CurrentUserService currentUsers) {
		this.currentUsers = currentUsers;
	}

	@Override
	public AuthorizationDecision authorize(Supplier<? extends Authentication> authentication,
			RequestAuthorizationContext context) {
		Authentication auth = authentication.get();
		if (!(auth instanceof JwtAuthenticationToken jwt) || !auth.isAuthenticated()) {
			return new AuthorizationDecision(false);
		}
		return new AuthorizationDecision(currentUsers.isEligible(jwt.getToken().getSubject()));
	}
}
