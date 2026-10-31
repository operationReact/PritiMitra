package com.pritimitra.backend;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pritimitra.backend.config.LegalProperties;
import com.pritimitra.backend.identity.EmailVerificationChecker;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DesignExperiencePreferenceApiTests {
	private static final String ADULT_DOB = "2000-01-01";
	@Autowired private MockMvc mvc;
	@Autowired private LegalProperties legal;
	@MockitoBean private EmailVerificationChecker emailVerification;

	@Test
	void defaultsToSparkAndPersistsValidatedExperienceForActiveAccount() throws Exception {
		String sub = eligibleUser();
		mvc.perform(get("/api/v1/me/preferences/design-experience").with(auth(sub)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.experience").value("spark"));

		mvc.perform(put("/api/v1/me/preferences/design-experience").with(auth(sub))
				.contentType("application/json").content("{\"experience\":\"Essence\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.experience").value("essence"));
		mvc.perform(get("/api/v1/me/preferences/design-experience").with(auth(sub)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.experience").value("essence"));

		mvc.perform(put("/api/v1/me/preferences/design-experience").with(auth(sub))
				.contentType("application/json").content("{\"experience\":\"neon\"}"))
			.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DESIGN_EXPERIENCE"));
		mvc.perform(get("/api/v1/me/preferences/design-experience").with(auth(sub)))
			.andExpect(status().isOk()).andExpect(jsonPath("$.experience").value("essence"));
	}

	@Test
	void rejectsUnauthenticatedAndIneligibleCallers() throws Exception {
		mvc.perform(get("/api/v1/me/preferences/design-experience"))
			.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/me/preferences/design-experience").with(auth("unknown-" + UUID.randomUUID())))
			.andExpect(status().isForbidden());
	}

	private String eligibleUser() throws Exception {
		String sub = "pref-" + UUID.randomUUID();
		when(emailVerification.isEmailVerified(anyString())).thenReturn(true);
		mvc.perform(put("/api/v1/onboarding/age-verification").with(auth(sub)).contentType("application/json")
				.content("{\"dateOfBirth\":\"" + ADULT_DOB + "\"}")).andExpect(status().isOk());
		mvc.perform(put("/api/v1/onboarding/consents").with(auth(sub)).contentType("application/json").content("""
				{"consents":[{"type":"TERMS_OF_SERVICE","policyVersion":"%s","accepted":true},
				             {"type":"PRIVACY_NOTICE","policyVersion":"%s","accepted":true}]}
				""".formatted(legal.termsVersion(), legal.privacyVersion()))).andExpect(status().isOk());
		mvc.perform(put("/api/v1/onboarding/profile").with(auth(sub)).contentType("application/json")
				.content("{\"displayName\":\"Pref\",\"goals\":[\"FRIENDSHIP\"]}")).andExpect(status().isOk());
		mvc.perform(post("/api/v1/onboarding/complete").with(auth(sub))).andExpect(status().isOk());
		return sub;
	}

	private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor auth(String sub) {
		return jwt().jwt(Jwt.withTokenValue("token-" + sub).header("alg", "RS256").subject(sub)
				.claim("token_use", "access").build()).authorities();
	}
}
