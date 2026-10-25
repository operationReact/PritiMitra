package com.pritimitra.backend.identity;

/**
 * Confirms with Amazon Cognito whether the email address of the signed-in user is verified. Access tokens do not
 * carry {@code email_verified}, so the check is made with the caller's access token.
 */
public interface EmailVerificationChecker {

	boolean isEmailVerified(String accessToken);
}
