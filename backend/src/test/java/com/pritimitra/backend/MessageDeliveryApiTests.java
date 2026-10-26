package com.pritimitra.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pritimitra.backend.config.LegalProperties;
import com.pritimitra.backend.identity.EmailVerificationChecker;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

/** Catch-up delivery, per-conversation ordering under concurrency, block-from-conversation, and notifications. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MessageDeliveryApiTests {

	private static final String ADULT_DOB = "2000-01-01";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private LegalProperties legal;

	@MockitoBean
	private EmailVerificationChecker emailVerification;

	@Test
	void catchUpStartsAfterNewestHistoryMessageAndDrainsBacklogAcrossPages() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String conversation = matchedConversation(a, b);
		List<String> beforeHistory = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			beforeHistory.add(send(a, conversation, "old" + i));
		}
		String latest = latestCursor(b, conversation);
		assertEquals(beforeHistory.get(2), newestHistoryId(b, conversation));

		List<String> sent = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			sent.add(send(a, conversation, "new" + i));
		}

		assertEquals(sent, drain(b, conversation, latest, 2));
	}

	@Test
	void catchUpFromEmptyConversationReceivesEveryMessageOnce() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String conversation = matchedConversation(a, b);
		String start = latestCursor(a, conversation);
		List<String> sent = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			sent.add(send(i % 2 == 0 ? a : b, conversation, "m" + i));
		}

		assertEquals(sent, drain(a, conversation, start, 2));
		assertEquals(sent, drain(b, conversation, start, 50));
	}

	@Test
	void retriedSendAppearsOnceInCatchUp() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String conversation = matchedConversation(a, b);
		String start = latestCursor(a, conversation);
		UUID clientId = UUID.randomUUID();
		String first = JsonPath.read(body(post(messagesUrl(conversation)).with(auth(a))
				.contentType("application/json").content(sendJson(clientId, "once"))), "$.messageId");
		String retry = JsonPath.read(body(post(messagesUrl(conversation)).with(auth(a))
				.contentType("application/json").content(sendJson(clientId, "once"))), "$.messageId");
		assertEquals(first, retry);

		assertEquals(List.of(first), drain(b, conversation, start, 10));
	}

	@Test
	void catchUpCursorIsScopedToItsConversationAndRejectsGarbage() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String c = eligibleUser("Cy");
		String ab = matchedConversation(a, b);
		String ac = matchedConversation(a, c);
		send(a, ab, "hello");
		String abCursor = latestCursor(a, ab);

		mvc.perform(get(newerUrl(ac, abCursor)).with(auth(a)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
		mvc.perform(get(newerUrl(ab, "not-a-cursor!!")).with(auth(a)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
		mvc.perform(get(messagesUrl(ab) + "/newer").with(auth(a)))
				.andExpect(status().isBadRequest());
		mvc.perform(get(newerUrl(ab, abCursor)).with(auth(c)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
	}

	@Test
	void concurrentWritersAndPollingReaderReceiveEveryMessageExactlyOnce() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String conversation = matchedConversation(a, b);
		String start = latestCursor(b, conversation);
		int writes = 16;

		ExecutorService pool = Executors.newFixedThreadPool(8);
		List<Future<Integer>> results = new ArrayList<>();
		try {
			for (int i = 0; i < writes; i++) {
				String sub = i % 2 == 0 ? a : b;
				String text = "race" + i;
				results.add(pool.submit(() -> mvc.perform(post(messagesUrl(conversation)).with(auth(sub))
						.contentType("application/json").content(sendJson(UUID.randomUUID(), text)))
						.andReturn().getResponse().getStatus()));
			}

			List<String> received = new ArrayList<>();
			String cursor = start;
			long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
			while (received.size() < writes && System.nanoTime() < deadline) {
				String page = body(get(newerUrl(conversation, cursor)).with(auth(b)));
				List<String> ids = JsonPath.read(page, "$.items[*].messageId");
				received.addAll(ids);
				cursor = JsonPath.read(page, "$.cursor");
				if (ids.isEmpty()) {
					Thread.sleep(10);
				}
			}

			for (Future<Integer> result : results) {
				assertEquals(200, result.get(60, TimeUnit.SECONDS));
			}
			assertEquals(writes, received.size());
			assertEquals(writes, new java.util.HashSet<>(received).size());
			assertEquals(writes, drain(b, conversation, start, 50).size());
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void conversationListOrdersByRecentActivityWithEmptyConversationsLastAndPaginatesWithoutDuplicates()
			throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String c = eligibleUser("Cy");
		String ab = matchedConversation(a, b);
		String ac = matchedConversation(a, c);

		send(a, ac, "first activity");
		assertEquals(List.of(ac, ab), listIds(a, 50));

		send(a, ab, "newer activity");
		assertEquals(List.of(ab, ac), listIds(a, 50));

		List<String> paged = new ArrayList<>();
		String cursor = null;
		do {
			String url = "/api/v1/conversations?size=1" + (cursor == null ? "" : "&cursor=" + cursor);
			String page = body(get(url).with(auth(a)));
			paged.addAll(JsonPath.read(page, "$.items[*].conversationId"));
			cursor = JsonPath.read(page, "$.nextCursor");
		} while (cursor != null);
		assertEquals(List.of(ab, ac), paged);
	}

	@Test
	void blockFromConversationUsesProfileIdEnforcesRulesAndClosesThread() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String outsider = eligibleUser("Cy");
		String conversation = matchedConversation(a, b);
		String bProfile = publicIdOf(b);

		String detail = body(get("/api/v1/conversations/" + conversation).with(auth(a)));
		assertEquals(bProfile, JsonPath.read(detail, "$.otherProfilePublicId"));
		assertEquals("ACTIVE", JsonPath.read(detail, "$.status"));

		mvc.perform(get("/api/v1/conversations/" + conversation).with(auth(outsider)))
				.andExpect(status().isNotFound());

		mvc.perform(put("/api/v1/blocks/" + bProfile).with(auth(a))).andExpect(status().isNoContent());

		assertEquals("UNMATCHED", JsonPath.read(body(get("/api/v1/conversations/" + conversation).with(auth(a))),
				"$.status"));
		mvc.perform(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
				.content(sendJson(UUID.randomUUID(), "still there?")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONVERSATION_CLOSED"));
		mvc.perform(post(messagesUrl(conversation)).with(auth(b)).contentType("application/json")
				.content(sendJson(UUID.randomUUID(), "hello")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONVERSATION_CLOSED"));
	}

	@Test
	void newMessageNotifiesRecipientOnceWithoutBodyAndNotSender() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String conversation = matchedConversation(a, b);
		UUID clientId = UUID.randomUUID();
		String secret = "private-words-" + UUID.randomUUID();

		for (int i = 0; i < 2; i++) {
			mvc.perform(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
					.content(sendJson(clientId, secret)))
					.andExpect(status().isOk());
		}

		String recipient = body(get("/api/v1/notifications?size=50").with(auth(b)));
		assertEquals(1, countKind(recipient, "NEW_MESSAGE"));
		assertTrue(!recipient.contains(secret), "notification payload must not contain message bodies");

		String sender = body(get("/api/v1/notifications?size=50").with(auth(a)));
		assertEquals(0, countKind(sender, "NEW_MESSAGE"));
	}

	private static int countKind(String page, String kind) {
		List<String> kinds = JsonPath.read(page, "$.items[*].kind");
		return (int) kinds.stream().filter(kind::equals).count();
	}

	private List<String> drain(String sub, String conversation, String start, int size) throws Exception {
		List<String> ids = new ArrayList<>();
		String cursor = start;
		boolean hasMore = true;
		while (hasMore) {
			String page = body(get(newerUrl(conversation, cursor, size)).with(auth(sub)));
			ids.addAll(JsonPath.read(page, "$.items[*].messageId"));
			cursor = JsonPath.read(page, "$.cursor");
			hasMore = JsonPath.read(page, "$.hasMore");
		}
		return ids;
	}

	private List<String> listIds(String sub, int size) throws Exception {
		return JsonPath.read(body(get("/api/v1/conversations?size=" + size).with(auth(sub))),
				"$.items[*].conversationId");
	}

	private String newestHistoryId(String sub, String conversation) throws Exception {
		return JsonPath.read(body(get(messagesUrl(conversation) + "?size=1").with(auth(sub))), "$.items[0].messageId");
	}

	private String latestCursor(String sub, String conversation) throws Exception {
		return JsonPath.read(body(get(messagesUrl(conversation) + "?size=2").with(auth(sub))), "$.latestCursor");
	}

	private String send(String sub, String conversation, String text) throws Exception {
		return JsonPath.read(body(post(messagesUrl(conversation)).with(auth(sub)).contentType("application/json")
				.content(sendJson(UUID.randomUUID(), text))), "$.messageId");
	}

	private String matchedConversation(String a, String b) throws Exception {
		body(put("/api/v1/likes/" + publicIdOf(b)).with(auth(a)));
		body(put("/api/v1/likes/" + publicIdOf(a)).with(auth(b)));
		String otherPub = publicIdOf(b);
		List<String> ids = JsonPath.read(body(get("/api/v1/conversations?size=50").with(auth(a))),
				"$.items[?(@.otherProfilePublicId == '" + otherPub + "')].conversationId");
		assertEquals(1, ids.size(), "expected exactly one conversation between the matched users");
		return ids.get(0);
	}

	private static String messagesUrl(String conversation) {
		return "/api/v1/conversations/" + conversation + "/messages";
	}

	private static String newerUrl(String conversation, String cursor) {
		return newerUrl(conversation, cursor, 20);
	}

	private static String newerUrl(String conversation, String cursor, int size) {
		return messagesUrl(conversation) + "/newer?size=" + size + "&cursor=" + cursor;
	}

	private static String sendJson(UUID clientMessageId, String body) {
		return "{\"clientMessageId\":\"" + clientMessageId + "\",\"body\":\"" + body + "\"}";
	}

	private String body(RequestBuilder request) throws Exception {
		return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private String publicIdOf(String sub) throws Exception {
		return JsonPath.read(body(get("/api/v1/profile").with(auth(sub))), "$.publicId");
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

	private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor auth(String sub) {
		return jwt().jwt(Jwt.withTokenValue("token-" + sub).header("alg", "RS256").subject(sub)
				.claim("token_use", "access").build()).authorities();
	}
}
