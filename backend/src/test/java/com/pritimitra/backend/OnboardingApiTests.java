package com.pritimitra.backend;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pritimitra.backend.config.LegalProperties;
import com.pritimitra.backend.identity.EmailVerificationChecker;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OnboardingApiTests {

	private static final String ADULT_DOB = "2000-01-01";
	private static final String MINOR_DOB = LocalDate.now().minusYears(15).toString();

	@Autowired
	private MockMvc mvc;

	@Autowired
	private LegalProperties legal;

	@MockitoBean
	private EmailVerificationChecker emailVerification;

	@Test
	void protectedEndpointRequiresAuthentication() throws Exception {
		mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void newAccountStartsPendingEmailVerificationAndPromotesWhenCognitoConfirms() throws Exception {
		String sub = newSub();
		when(emailVerification.isEmailVerified(anyString())).thenReturn(false);
		mvc.perform(get("/api/v1/me").with(auth(sub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING_EMAIL_VERIFICATION"));

		when(emailVerification.isEmailVerified(anyString())).thenReturn(true);
		mvc.perform(get("/api/v1/me").with(auth(sub)))
				.andExpect(jsonPath("$.status").value("ONBOARDING_INCOMPLETE"));
	}

	@Test
	void unverifiedEmailCannotProceedToAgeVerification() throws Exception {
		String sub = newSub();
		when(emailVerification.isEmailVerified(anyString())).thenReturn(false);
		mvc.perform(put("/api/v1/onboarding/age-verification").with(auth(sub)).contentType("application/json")
				.content("{\"dateOfBirth\":\"" + ADULT_DOB + "\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
	}

	@Test
	void underageUserIsIneligibleAndCannotReachProtectedApis() throws Exception {
		String sub = newSub();
		verified();
		mvc.perform(put("/api/v1/onboarding/age-verification").with(auth(sub)).contentType("application/json")
				.content("{\"dateOfBirth\":\"" + MINOR_DOB + "\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("AGE_INELIGIBLE"));

		mvc.perform(get("/api/v1/onboarding").with(auth(sub)))
				.andExpect(jsonPath("$.status").value("INELIGIBLE"));
		mvc.perform(get("/api/v1/discovery").with(auth(sub)))
				.andExpect(status().isForbidden());
	}

	@Test
	void dateOfBirthCannotBeChangedAfterVerification() throws Exception {
		String sub = newSub();
		verified();
		mvc.perform(put("/api/v1/onboarding/age-verification").with(auth(sub)).contentType("application/json")
				.content("{\"dateOfBirth\":\"" + ADULT_DOB + "\"}"))
				.andExpect(status().isOk());
		mvc.perform(put("/api/v1/onboarding/age-verification").with(auth(sub)).contentType("application/json")
				.content("{\"dateOfBirth\":\"1990-05-05\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DATE_OF_BIRTH_LOCKED"));
	}

	@Test
	void consentMustMatchCurrentPolicyVersion() throws Exception {
		String sub = newSub();
		verified();
		mvc.perform(put("/api/v1/onboarding/age-verification").with(auth(sub)).contentType("application/json")
				.content("{\"dateOfBirth\":\"" + ADULT_DOB + "\"}"));
		mvc.perform(put("/api/v1/onboarding/consents").with(auth(sub)).contentType("application/json")
				.content(consents("1999-01")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("POLICY_VERSION_MISMATCH"));
	}

	@Test
	void completionRequiresDisplayNameAndGoalButNotDiscoveryPreferences() throws Exception {
		String sub = newSub();
		verified();
		onboardAdult(sub);
		mvc.perform(post("/api/v1/onboarding/complete").with(auth(sub)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PROFILE_INCOMPLETE"));

		mvc.perform(put("/api/v1/onboarding/profile").with(auth(sub)).contentType("application/json")
				.content("{\"displayName\":\"Asha\",\"goals\":[\"OPEN_TO_CONNECTION\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discoveryPreferencesConfigured").value(false));

		mvc.perform(post("/api/v1/onboarding/complete").with(auth(sub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void discoveryPreferencesMustBeProvidedTogether() throws Exception {
		String sub = newSub();
		verified();
		onboardAdult(sub);
		mvc.perform(put("/api/v1/onboarding/profile").with(auth(sub)).contentType("application/json")
				.content("{\"displayName\":\"Asha\",\"goals\":[\"DATING\"],\"discoveryMinAge\":25}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INCOMPLETE_DISCOVERY_PREFERENCES"));
	}

	@Test
	void activeUserPassesEligibilityGateForProtectedApis() throws Exception {
		String sub = newSub();
		verified();
		onboardAdult(sub);
		mvc.perform(put("/api/v1/onboarding/profile").with(auth(sub)).contentType("application/json")
				.content("{\"displayName\":\"Asha\",\"goals\":[\"FRIENDSHIP\"]}"));
		mvc.perform(post("/api/v1/onboarding/complete").with(auth(sub)));

		mvc.perform(get("/api/v1/discovery").with(auth(sub)))
				.andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/me").with(auth(sub)))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":\"ACTIVE\"")));
	}

	private void verified() {
		when(emailVerification.isEmailVerified(anyString())).thenReturn(true);
	}

	@Test
	void unknownInterestedInCodeIsRejectedWithStableCode() throws Exception {
		String sub = newSub();
		verified();
		onboardAdult(sub);
		mvc.perform(put("/api/v1/onboarding/profile").with(auth(sub)).contentType("application/json")
				.content(profileWithInterests("\"NOT_A_CODE\"")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void allIdentitiesCannotBeCombinedWithSpecificIdentities() throws Exception {
		String sub = newSub();
		verified();
		onboardAdult(sub);
		mvc.perform(put("/api/v1/onboarding/profile").with(auth(sub)).contentType("application/json")
				.content(profileWithInterests("\"ALL_IDENTITIES\",\"MEN\"")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_DISCOVERY_INTEREST"));
	}

	@Test
	void multipleSpecificIdentitiesAreAcceptedWhenAllDiscoveryFieldsAreProvided() throws Exception {
		String sub = newSub();
		verified();
		onboardAdult(sub);
		mvc.perform(put("/api/v1/onboarding/profile").with(auth(sub)).contentType("application/json")
				.content(profileWithInterests("\"MEN\",\"NON_BINARY\"")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discoveryPreferencesConfigured").value(true));
	}

	@Test
	void interestsAloneAreRejectedAsIncompleteDiscoveryPreferences() throws Exception {
		String sub = newSub();
		verified();
		onboardAdult(sub);
		mvc.perform(put("/api/v1/onboarding/profile").with(auth(sub)).contentType("application/json")
				.content("{\"displayName\":\"Asha\",\"goals\":[\"DATING\"],\"discoveryInterestedIn\":[\"WOMEN\"]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INCOMPLETE_DISCOVERY_PREFERENCES"));
	}

	private static String profileWithInterests(String interestsJson) {
		return """
				{"displayName":"Asha","goals":["DATING"],"discoveryInterestedIn":[%s],
				 "discoveryMinAge":25,"discoveryMaxAge":35,"discoveryMaxDistanceKm":10}
				""".formatted(interestsJson);
	}

	private void onboardAdult(String sub) throws Exception {
		mvc.perform(put("/api/v1/onboarding/age-verification").with(auth(sub)).contentType("application/json")
				.content("{\"dateOfBirth\":\"" + ADULT_DOB + "\"}"))
				.andExpect(status().isOk());
		mvc.perform(put("/api/v1/onboarding/consents").with(auth(sub)).contentType("application/json")
				.content(currentConsents()))
				.andExpect(status().isOk());
	}

	private String currentConsents() {
		return consents(legal.termsVersion(), legal.privacyVersion());
	}

	private static String consents(String version) {
		return consents(version, version);
	}

	private static String consents(String termsVersion, String privacyVersion) {
		return """
				{"consents":[
				  {"type":"TERMS_OF_SERVICE","policyVersion":"%s","accepted":true},
				  {"type":"PRIVACY_NOTICE","policyVersion":"%s","accepted":true}]}
				""".formatted(termsVersion, privacyVersion);
	}

	private static String newSub() {
		return "sub-" + UUID.randomUUID();
	}

	private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor auth(String sub) {
		return jwt().jwt(Jwt.withTokenValue("token-" + sub).header("alg", "RS256").subject(sub)
				.claim("token_use", "access").build()).authorities();
	}
}
