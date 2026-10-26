package com.pritimitra.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MatchingApiTests {

	private static final String ADULT_DOB = "2000-01-01";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private LegalProperties legal;

	@MockitoBean
	private EmailVerificationChecker emailVerification;

	@Test
	void unilateralLikeDoesNotCreateAMatch() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");

		assertEquals("NONE", likeState(a, publicIdOf(b)));
		assertEquals(0, matchesOf(a).size());
		assertEquals(0, matchesOf(b).size());
	}

	@Test
	void reciprocalLikeCreatesOneMatchAndRepeatsAreIdempotent() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String aId = publicIdOf(a);
		String bId = publicIdOf(b);

		assertEquals("NONE", likeState(a, bId));
		String first = likeBody(b, aId);
		assertEquals("CREATED", JsonPath.read(first, "$.matchState"));
		String matchId = JsonPath.read(first, "$.matchPublicId");
		assertNotNull(matchId);

		assertEquals("EXISTING", likeState(b, aId));
		assertEquals("EXISTING", likeState(a, bId));
		assertEquals(1, matchesOf(a).size());
		assertEquals(matchId, matchesOf(a).get(0));
		assertEquals(matchId, matchesOf(b).get(0));
	}

	@Test
	void matchesAreVisibleOnlyToParticipants() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String outsider = eligibleUser("Cy");
		likeBody(a, publicIdOf(b));
		String matchId = JsonPath.read(likeBody(b, publicIdOf(a)), "$.matchPublicId");

		mvc.perform(get("/api/v1/matches/" + matchId).with(auth(a)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"));
		mvc.perform(get("/api/v1/matches/" + matchId).with(auth(outsider)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("MATCH_NOT_FOUND"));
		mvc.perform(get("/api/v1/matches/" + UUID.randomUUID()).with(auth(a)))
				.andExpect(status().isNotFound());
		assertEquals(0, matchesOf(outsider).size());
	}

	@Test
	void unmatchIsIdempotentAndRequiresFreshMutualIntentToRematch() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String aId = publicIdOf(a);
		String bId = publicIdOf(b);
		likeBody(a, bId);
		String matchId = JsonPath.read(likeBody(b, aId), "$.matchPublicId");

		mvc.perform(delete("/api/v1/matches/" + matchId).with(auth(a)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UNMATCHED"));
		mvc.perform(delete("/api/v1/matches/" + matchId).with(auth(b)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UNMATCHED"));
		assertEquals(0, matchesOf(a).size());

		// Unmatch removed both likes, so a single new like must not restore the match.
		assertEquals("NONE", likeState(b, aId));
		assertEquals(0, matchesOf(a).size());

		assertEquals("CREATED", likeState(a, bId));
		assertEquals(1, matchesOf(a).size());
	}

	@Test
	void concurrentReciprocalLikesConvergeOnOneMatch() throws Exception {
		for (int round = 0; round < 5; round++) {
			String a = eligibleUser("Ada" + round);
			String b = eligibleUser("Ben" + round);
			String aId = publicIdOf(a);
			String bId = publicIdOf(b);
			CountDownLatch start = new CountDownLatch(1);
			ExecutorService pool = Executors.newFixedThreadPool(2);
			try {
				Future<String> fromA = pool.submit(awaitThen(start, () -> likeState(a, bId)));
				Future<String> fromB = pool.submit(awaitThen(start, () -> likeState(b, aId)));
				start.countDown();
				List<String> states = List.of(fromA.get(60, java.util.concurrent.TimeUnit.SECONDS),
						fromB.get(60, java.util.concurrent.TimeUnit.SECONDS));
				assertEquals(1, states.stream().filter("CREATED"::equals).count(),
						"exactly one reciprocal like must create the match: " + states);
			} finally {
				pool.shutdownNow();
			}
			assertEquals(1, matchesOf(a).size());
			assertEquals(1, matchesOf(b).size());
		}
	}

	@Test
	void listPaginatesWithOpaqueCursorsAndValidatesSize() throws Exception {
		String a = eligibleUser("Ada");
		for (int i = 0; i < 3; i++) {
			String other = eligibleUser("Other" + i);
			likeBody(a, publicIdOf(other));
			likeBody(other, publicIdOf(a));
		}
		List<String> seen = new ArrayList<>();
		String cursor = null;
		do {
			String url = "/api/v1/matches?size=2" + (cursor == null ? "" : "&cursor=" + cursor);
			String body = body(get(url).with(auth(a)));
			seen.addAll(JsonPath.read(body, "$.items[*].matchPublicId"));
			cursor = JsonPath.read(body, "$.nextCursor");
		} while (cursor != null);
		assertEquals(3, seen.size());
		assertEquals(3, seen.stream().distinct().count());
		mvc.perform(get("/api/v1/matches?size=0").with(auth(a))).andExpect(status().isBadRequest());
	}

	private String likeBody(String sub, String targetPublicId) throws Exception {
		return body(put("/api/v1/likes/" + targetPublicId).with(auth(sub)));
	}

	private String likeState(String sub, String targetPublicId) throws Exception {
		return JsonPath.read(likeBody(sub, targetPublicId), "$.matchState");
	}

	private List<String> matchesOf(String sub) throws Exception {
		List<String> ids = new ArrayList<>();
		String cursor = null;
		do {
			String url = "/api/v1/matches?size=50" + (cursor == null ? "" : "&cursor=" + cursor);
			String body = body(get(url).with(auth(sub)));
			ids.addAll(JsonPath.read(body, "$.items[*].matchPublicId"));
			cursor = JsonPath.read(body, "$.nextCursor");
		} while (cursor != null);
		return ids;
	}

	private String publicIdOf(String sub) throws Exception {
		return JsonPath.read(body(get("/api/v1/profile").with(auth(sub))), "$.publicId");
	}

	private String body(RequestBuilder request) throws Exception {
		return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private String eligibleUser(String displayName) throws Exception {
		String sub = "sub-" + UUID.randomUUID();
		when(emailVerification.isEmailVerified(anyString())).thenReturn(true);
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
		mvc.perform(put("/api/v1/onboarding/profile").with(auth(sub)).contentType("application/json")
				.content("{\"displayName\":\"" + displayName + "\",\"goals\":[\"FRIENDSHIP\"]}"))
				.andExpect(status().isOk());
		mvc.perform(post("/api/v1/onboarding/complete").with(auth(sub))).andExpect(status().isOk());
		mvc.perform(put("/api/v1/profile/discoverability").with(auth(sub)).contentType("application/json")
				.content("{\"discoverable\":true}"))
				.andExpect(status().isOk());
		return sub;
	}

	private static <T> Callable<T> awaitThen(CountDownLatch start, Callable<T> action) {
		return () -> {
			start.await();
			return action.call();
		};
	}

	private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor auth(String sub) {
		return jwt().jwt(Jwt.withTokenValue("token-" + sub).header("alg", "RS256").subject(sub)
				.claim("token_use", "access").build()).authorities();
	}
}
