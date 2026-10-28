package com.pritimitra.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MessagingApiTests {

	private static final String ADULT_DOB = "2000-01-01";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private LegalProperties legal;

	@MockitoBean
	private EmailVerificationChecker emailVerification;

	@Test
	void sendPersistsForBothParticipantsAndSenderIsServerSide() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String conversation = matchedConversation(a, b);

		String sent = body(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
				.content(sendJson(UUID.randomUUID(), "  hello there  ")));
		assertEquals("hello there", JsonPath.read(sent, "$.body"));
		assertEquals(true, JsonPath.read(sent, "$.mine"));

		// A spoofed sender field is ignored; the authenticated subject is the sender.
		mvc.perform(post(messagesUrl(conversation)).with(auth(b)).contentType("application/json")
				.content("{\"clientMessageId\":\"" + UUID.randomUUID() + "\",\"body\":\"hi\",\"senderId\":\""
						+ UUID.randomUUID() + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mine").value(true));

		List<Boolean> mineForA = mineFlags(body(get(messagesUrl(conversation)).with(auth(a))));
		List<Boolean> mineForB = mineFlags(body(get(messagesUrl(conversation)).with(auth(b))));
		assertEquals(List.of(false, true), mineForA);
		assertEquals(List.of(true, false), mineForB);
	}

	@Test
	void retryWithSameClientIdReturnsStoredMessageAndDifferentContentIsRejected() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String conversation = matchedConversation(a, b);
		UUID clientId = UUID.randomUUID();

		String first = body(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
				.content(sendJson(clientId, "once")));
		String retry = body(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
				.content(sendJson(clientId, "once")));
		assertEquals(JsonPath.<String>read(first, "$.messageId"), JsonPath.<String>read(retry, "$.messageId"));
		assertEquals(1, messagesOf(a, conversation).size());

		mvc.perform(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
				.content(sendJson(clientId, "different")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
		assertEquals(1, messagesOf(a, conversation).size());
	}

	@Test
	void messageSendingEnforcesBurstLimitButAllowsIdempotentRetries() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String conversation = matchedConversation(a, b);
		UUID lastClientId = UUID.randomUUID();
		String lastBody = "burst-0";
		for (int i = 0; i < 30; i++) {
			lastClientId = UUID.randomUUID();
			lastBody = "burst-" + i;
			mvc.perform(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
					.content(sendJson(lastClientId, lastBody)))
				.andExpect(status().isOk());
		}

		// Idempotent retry of a persisted message is acknowledged and does not consume a new rate-limit slot.
		mvc.perform(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
				.content(sendJson(lastClientId, lastBody)))
			.andExpect(status().isOk());

		mvc.perform(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
				.content(sendJson(UUID.randomUUID(), "burst-over-limit")))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("MESSAGE_RATE_LIMITED"));
		assertEquals(30, messagesOf(a, conversation).size());
	}

	@Test
	void invalidBodiesAndIdentifiersAreRejected() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String conversation = matchedConversation(a, b);

		mvc.perform(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
				.content(sendJson(UUID.randomUUID(), "   ")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_MESSAGE"));
		mvc.perform(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
				.content(sendJson(UUID.randomUUID(), "x".repeat(2001))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MESSAGE_TOO_LONG"));
		mvc.perform(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
				.content("{\"body\":\"no client id\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
				.content(sendJson(UUID.randomUUID(), "x".repeat(2000))))
				.andExpect(status().isOk());
		mvc.perform(get("/api/v1/conversations/not-a-uuid/messages").with(auth(a)))
				.andExpect(status().isBadRequest());
		mvc.perform(get(messagesUrl(conversation) + "?size=0").with(auth(a)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void nonParticipantsCannotReadOrSendAndSeeNoConversations() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String outsider = eligibleUser("Cy");
		String conversation = matchedConversation(a, b);
		mvc.perform(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
				.content(sendJson(UUID.randomUUID(), "private")))
				.andExpect(status().isOk());

		mvc.perform(get(messagesUrl(conversation)).with(auth(outsider)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
		mvc.perform(post(messagesUrl(conversation)).with(auth(outsider)).contentType("application/json")
				.content(sendJson(UUID.randomUUID(), "intrude")))
				.andExpect(status().isNotFound());
		assertEquals(0, conversationsOf(outsider).size());
		assertEquals(1, conversationsOf(a).size());
	}

	@Test
	void unmatchClosesSendsButKeepsHistoryReadable() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String matchId = likeThenMatch(a, b);
		String conversation = conversationOf(a);
		UUID retryId = UUID.randomUUID();
		String sent = body(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
				.content(sendJson(retryId, "before unmatch")));

		mvc.perform(delete("/api/v1/matches/" + matchId).with(auth(b))).andExpect(status().isOk());

		mvc.perform(post(messagesUrl(conversation)).with(auth(b)).contentType("application/json")
				.content(sendJson(UUID.randomUUID(), "after unmatch")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONVERSATION_CLOSED"));

		// A retry of a message stored before unmatch is still acknowledged with the same id.
		String retried = body(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
				.content(sendJson(retryId, "before unmatch")));
		assertEquals(JsonPath.<String>read(sent, "$.messageId"), JsonPath.<String>read(retried, "$.messageId"));

		List<String> bodies = bodiesOf(a, conversation);
		assertEquals(List.of("before unmatch"), bodies);
		assertEquals("UNMATCHED", JsonPath.read(body(get("/api/v1/conversations?size=20").with(auth(a))),
				"$.items[0].status"));
	}

	@Test
	void historyPaginatesNewestFirstWithoutDuplicates() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String conversation = matchedConversation(a, b);
		List<String> sent = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			String text = "m" + i;
			sent.add(text);
			mvc.perform(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
					.content(sendJson(UUID.randomUUID(), text)))
					.andExpect(status().isOk());
		}

		List<String> seen = new ArrayList<>();
		String cursor = null;
		do {
			String url = messagesUrl(conversation) + "?size=2" + (cursor == null ? "" : "&cursor=" + cursor);
			String page = body(get(url).with(auth(b)));
			seen.addAll(JsonPath.read(page, "$.items[*].body"));
			cursor = JsonPath.read(page, "$.nextCursor");
		} while (cursor != null);

		List<String> expected = new ArrayList<>(sent);
		java.util.Collections.reverse(expected);
		assertEquals(expected, seen);
	}

	@Test
	void unreadCountFollowsReadCursorAndNeverMovesBackwards() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String conversation = matchedConversation(a, b);
		String first = body(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
				.content(sendJson(UUID.randomUUID(), "one")));
		String second = body(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
				.content(sendJson(UUID.randomUUID(), "two")));

		assertEquals(2, unreadOf(b, conversation));
		assertEquals(0, unreadOf(a, conversation));

		String readSecond = JsonPath.read(second, "$.messageId");
		mvc.perform(put(messagesUrl(conversation).replace("/messages", "/read")).with(auth(b))
				.contentType("application/json").content("{\"messageId\":\"" + readSecond + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unreadCount").value(0));

		String readFirst = JsonPath.read(first, "$.messageId");
		mvc.perform(put(messagesUrl(conversation).replace("/messages", "/read")).with(auth(b))
				.contentType("application/json").content("{\"messageId\":\"" + readFirst + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unreadCount").value(0));
		assertEquals(0, unreadOf(b, conversation));
	}

	private String matchedConversation(String a, String b) throws Exception {
		likeThenMatch(a, b);
		return conversationOf(a);
	}

	private String likeThenMatch(String a, String b) throws Exception {
		body(put("/api/v1/likes/" + publicIdOf(b)).with(auth(a)));
		return JsonPath.read(body(put("/api/v1/likes/" + publicIdOf(a)).with(auth(b))), "$.matchPublicId");
	}

	private String conversationOf(String sub) throws Exception {
		List<String> ids = conversationsOf(sub);
		assertTrue(!ids.isEmpty(), "expected a conversation for matched user");
		return ids.get(0);
	}

	private List<String> conversationsOf(String sub) throws Exception {
		return JsonPath.read(body(get("/api/v1/conversations?size=50").with(auth(sub))), "$.items[*].conversationId");
	}

	private List<String> messagesOf(String sub, String conversation) throws Exception {
		return JsonPath.read(body(get(messagesUrl(conversation)).with(auth(sub))), "$.items[*].messageId");
	}

	private List<String> bodiesOf(String sub, String conversation) throws Exception {
		return JsonPath.read(body(get(messagesUrl(conversation)).with(auth(sub))), "$.items[*].body");
	}

	private List<Boolean> mineFlags(String page) {
		List<Boolean> flags = JsonPath.read(page, "$.items[*].mine");
		return flags.stream().toList();
	}

	private int unreadOf(String sub, String conversation) throws Exception {
		String list = body(get("/api/v1/conversations?size=50").with(auth(sub)));
		List<Integer> counts = JsonPath.read(list, "$.items[?(@.conversationId == '" + conversation + "')].unreadCount");
		return counts.get(0);
	}

	private static String messagesUrl(String conversation) {
		return "/api/v1/conversations/" + conversation + "/messages";
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
