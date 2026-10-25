package com.pritimitra.backend;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pritimitra.backend.config.LegalProperties;
import com.pritimitra.backend.identity.EmailVerificationChecker;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.web.servlet.RequestBuilder;

/** Phase 4 profile, privacy, discovery and like behaviour, exercised end to end through the HTTP boundary. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ProfileDiscoveryApiTests {

	private static final String ADULT_DOB = "2000-01-01";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private LegalProperties legal;

	@MockitoBean
	private EmailVerificationChecker emailVerification;

	@Test
	void unverifiedAccountCannotReadOwnProfile() throws Exception {
		mvc.perform(get("/api/v1/profile").with(auth(newSub()))).andExpect(status().isForbidden());
	}

	@Test
	void onboardingIncompleteAccountCannotReadProfileOrDiscovery() throws Exception {
		String sub = newSub();
		verified();
		onboardAdult(sub);
		mvc.perform(get("/api/v1/profile").with(auth(sub))).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/discovery").with(auth(sub))).andExpect(status().isForbidden());
	}

	@Test
	void underageAccountCannotReachProfileDiscoveryOrLikes() throws Exception {
		String sub = newSub();
		verified();
		mvc.perform(put("/api/v1/onboarding/age-verification").with(auth(sub)).contentType("application/json")
				.content("{\"dateOfBirth\":\"" + LocalDate.now().minusYears(15) + "\"}"));
		mvc.perform(get("/api/v1/profile").with(auth(sub))).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/discovery").with(auth(sub))).andExpect(status().isForbidden());
		mvc.perform(put("/api/v1/likes/" + UUID.randomUUID()).with(auth(sub))).andExpect(status().isForbidden());
	}

	@Test
	void profilesArePrivateByDefaultAndProfileUpdateCannotSetVisibility() throws Exception {
		String sub = newActiveUser("Asha", "DATING");
		mvc.perform(get("/api/v1/profile").with(auth(sub)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discoverable").value(false));
		mvc.perform(put("/api/v1/profile").with(auth(sub)).contentType("application/json")
				.content("{\"displayName\":\"Asha\",\"goals\":[\"DATING\"],\"discoverable\":true,\"status\":\"SUSPENDED\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discoverable").value(false));
	}

	@Test
	void profileUpdateValidatesFieldsAndChangesOnlyTheCallersProfile() throws Exception {
		String owner = newActiveUser("Owner", "FRIENDSHIP");
		String other = newActiveUser("Other", "FRIENDSHIP");
		mvc.perform(put("/api/v1/profile").with(auth(owner)).contentType("application/json")
				.content("{\"displayName\":\"   \",\"goals\":[\"DATING\"]}"))
				.andExpect(status().isBadRequest());
		mvc.perform(put("/api/v1/profile").with(auth(owner)).contentType("application/json")
				.content("{\"displayName\":\"Renamed\",\"goals\":[\"DATING\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("Renamed"));
		mvc.perform(get("/api/v1/profile").with(auth(other)))
				.andExpect(jsonPath("$.displayName").value("Other"));
	}

	@Test
	void discoveryIncludesOptedInProfilesAndExcludesPrivateProfilesAndSelf() throws Exception {
		String visible = newActiveUser("Visible", "DATING");
		makeDiscoverable(visible);
		String hidden = newActiveUser("Hidden", "DATING");
		String viewer = newActiveUser("Viewer", "DATING");
		makeDiscoverable(viewer);

		List<String> viewerSees = discoverableIds(viewer, 50);
		assertTrue(viewerSees.contains(publicIdOf(visible)));
		assertFalse(viewerSees.contains(publicIdOf(hidden)));
		assertFalse(viewerSees.contains(publicIdOf(viewer)));
	}

	@Test
	void discoveryCardsExposeOnlyApprovedFields() throws Exception {
		String sub = newActiveUser("Lena", "COMMUNITY");
		makeDiscoverable(sub);
		String viewer = newActiveUser("Viewer2", "COMMUNITY");
		String body = body(get("/api/v1/discovery?size=50").with(auth(viewer)));
		assertTrue(body.contains("Lena"));
		assertThat(body, not(containsString(sub)));
		assertThat(body, not(containsString("dateOfBirth")));
		assertThat(body, not(containsString("email")));
		assertThat(body, not(containsString("cognito")));
	}

	@Test
	void likesAreIdempotentAndDirectionalAndRemoveProfilesFromDiscovery() throws Exception {
		String target = newActiveUser("Target", "FRIENDSHIP");
		makeDiscoverable(target);
		String viewer = newActiveUser("Liker", "FRIENDSHIP");
		makeDiscoverable(viewer);
		String targetId = publicIdOf(target);

		mvc.perform(put("/api/v1/likes/" + targetId).with(auth(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(true));
		mvc.perform(put("/api/v1/likes/" + targetId).with(auth(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(true));
		assertFalse(discoverableIds(viewer, 50).contains(targetId));
		// Likes are directional: the target has not liked the viewer, so the viewer remains visible to the target.
		assertTrue(discoverableIds(target, 50).contains(publicIdOf(viewer)));

		mvc.perform(delete("/api/v1/likes/" + targetId).with(auth(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(false));
		mvc.perform(delete("/api/v1/likes/" + targetId).with(auth(viewer)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(false));
		assertTrue(discoverableIds(viewer, 50).contains(targetId));
	}

	@Test
	void selfLikeIsRejected() throws Exception {
		String sub = newActiveUser("Mirror", "DATING");
		makeDiscoverable(sub);
		mvc.perform(put("/api/v1/likes/" + publicIdOf(sub)).with(auth(sub)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CANNOT_LIKE_SELF"));
	}

	@Test
	void likingPrivateOrUnknownProfilesReturnsTheSameNotFound() throws Exception {
		String hidden = newActiveUser("Quiet", "DATING");
		String viewer = newActiveUser("Seeker", "DATING");
		makeDiscoverable(viewer);
		mvc.perform(put("/api/v1/likes/" + publicIdOf(hidden)).with(auth(viewer)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
		mvc.perform(put("/api/v1/likes/" + UUID.randomUUID()).with(auth(viewer)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
	}

	@Test
	void discoveryRejectsInvalidQueryParameters() throws Exception {
		String viewer = newActiveUser("Checker", "DATING");
		mvc.perform(get("/api/v1/discovery?goal=NOT_A_GOAL").with(auth(viewer)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_GOAL"));
		mvc.perform(get("/api/v1/discovery?size=0").with(auth(viewer)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));
		mvc.perform(get("/api/v1/discovery?size=51").with(auth(viewer)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));
		mvc.perform(get("/api/v1/discovery?cursor=!!!not-base64").with(auth(viewer)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
	}

	@Test
	void viewerAgeWindowExcludesCandidatesOutsideTheRange() throws Exception {
		String candidate = newActiveUser("Twenties", "DATING");
		makeDiscoverable(candidate);
		String viewer = newActiveUser("Senior", "DATING");
		makeDiscoverable(viewer);
		mvc.perform(put("/api/v1/profile").with(auth(viewer)).contentType("application/json")
				.content("""
						{"displayName":"Senior","goals":["DATING"],"discoveryInterestedIn":["MEN"],
						 "discoveryMinAge":60,"discoveryMaxAge":70,"discoveryMaxDistanceKm":10}
						"""))
				.andExpect(status().isOk());
		assertFalse(discoverableIds(viewer, 50).contains(publicIdOf(candidate)));
	}

	@Test
	void paginationReturnsEveryCandidateExactlyOnceWithOpaqueCursors() throws Exception {
		String first = newActiveUser("P1", "MEET_PEOPLE");
		String second = newActiveUser("P2", "MEET_PEOPLE");
		String third = newActiveUser("P3", "MEET_PEOPLE");
		makeDiscoverable(first);
		makeDiscoverable(second);
		makeDiscoverable(third);
		String viewer = newActiveUser("Walker", "MEET_PEOPLE");

		List<String> walked = discoverableIds(viewer, 1);
		assertEquals(walked.size(), walked.stream().distinct().count());
		assertTrue(walked.containsAll(List.of(publicIdOf(first), publicIdOf(second), publicIdOf(third))));
	}

	private void makeDiscoverable(String sub) throws Exception {
		mvc.perform(put("/api/v1/profile/discoverability").with(auth(sub)).contentType("application/json")
				.content("{\"discoverable\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.discoverable").value(true));
	}

	private String publicIdOf(String sub) throws Exception {
		return JsonPath.read(body(get("/api/v1/profile").with(auth(sub))), "$.publicId");
	}

	private List<String> discoverableIds(String sub, int size) throws Exception {
		List<String> ids = new ArrayList<>();
		String cursor = null;
		int pages = 0;
		do {
			String url = "/api/v1/discovery?size=" + size + (cursor == null ? "" : "&cursor=" + cursor);
			String body = body(get(url).with(auth(sub)));
			ids.addAll(JsonPath.read(body, "$.items[*].publicId"));
			cursor = JsonPath.read(body, "$.nextCursor");
			pages++;
		} while (cursor != null && pages < 500);
		return ids;
	}

	private String body(RequestBuilder request) throws Exception {
		return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private String newActiveUser(String displayName, String goal) throws Exception {
		String sub = newSub();
		verified();
		onboardAdult(sub);
		mvc.perform(put("/api/v1/onboarding/profile").with(auth(sub)).contentType("application/json")
				.content("{\"displayName\":\"" + displayName + "\",\"goals\":[\"" + goal + "\"]}"))
				.andExpect(status().isOk());
		mvc.perform(post("/api/v1/onboarding/complete").with(auth(sub)))
				.andExpect(status().isOk());
		return sub;
	}

	private void verified() {
		when(emailVerification.isEmailVerified(anyString())).thenReturn(true);
	}

	private void onboardAdult(String sub) throws Exception {
		mvc.perform(put("/api/v1/onboarding/age-verification").with(auth(sub)).contentType("application/json")
				.content("{\"dateOfBirth\":\"" + ADULT_DOB + "\"}"))
				.andExpect(status().isOk());
		mvc.perform(put("/api/v1/onboarding/consents").with(auth(sub)).contentType("application/json")
				.content("""
						{"consents":[
						  {"type":"TERMS_OF_SERVICE","policyVersion":"%s","accepted":true},
						  {"type":"PRIVACY_NOTICE","policyVersion":"%s","accepted":true}]}
						""".formatted(legal.termsVersion(), legal.privacyVersion())))
				.andExpect(status().isOk());
	}

	private static String newSub() {
		return "sub-" + UUID.randomUUID();
	}

	private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor auth(String sub) {
		return jwt().jwt(Jwt.withTokenValue("token-" + sub).header("alg", "RS256").subject(sub)
				.claim("token_use", "access").build()).authorities();
	}
}
