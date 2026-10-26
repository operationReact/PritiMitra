package com.pritimitra.backend;

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
class SafetyApiTests {

	private static final String ADULT_DOB = "2000-01-01";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private LegalProperties legal;

	@MockitoBean
	private EmailVerificationChecker emailVerification;

	@Test
	void blockRemovesMatchAndClosesConversationForBothSides() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String conversation = matchedConversation(a, b);

		mvc.perform(put(blocksUrl(publicIdOf(b))).with(auth(a))).andExpect(status().isNoContent());

		mvc.perform(post(messagesUrl(conversation)).with(auth(b)).contentType("application/json")
				.content(sendJson(UUID.randomUUID(), "still there?")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CONVERSATION_CLOSED"));
		mvc.perform(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
				.content(sendJson(UUID.randomUUID(), "hello")))
				.andExpect(status().isConflict());
	}

	@Test
	void blockedPairCannotLikeOrMatchInEitherDirection() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		mvc.perform(put(blocksUrl(publicIdOf(b))).with(auth(a))).andExpect(status().isNoContent());

		mvc.perform(put("/api/v1/likes/" + publicIdOf(b)).with(auth(a)))
				.andExpect(status().isNotFound());
		mvc.perform(put("/api/v1/likes/" + publicIdOf(a)).with(auth(b)))
				.andExpect(status().isNotFound());
	}

	@Test
	void blockedUsersAreExcludedFromDiscoveryInBothDirections() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		mvc.perform(put(blocksUrl(publicIdOf(b))).with(auth(a))).andExpect(status().isNoContent());

		assertFalse(discoveryIds(a).contains(publicIdOf(b)));
		assertFalse(discoveryIds(b).contains(publicIdOf(a)));
	}

	@Test
	void blockIsIdempotentAndSelfBlockIsRejected() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");

		mvc.perform(put(blocksUrl(publicIdOf(b))).with(auth(a))).andExpect(status().isNoContent());
		mvc.perform(put(blocksUrl(publicIdOf(b))).with(auth(a))).andExpect(status().isNoContent());

		mvc.perform(put(blocksUrl(publicIdOf(a))).with(auth(a)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CANNOT_BLOCK_SELF"));
	}

	@Test
	void blockingUnknownIdentifiersReturnsNoContentWithoutRevealingAccounts() throws Exception {
		String a = eligibleUser("Ada");

		mvc.perform(put(blocksUrl(UUID.randomUUID().toString())).with(auth(a)))
				.andExpect(status().isNoContent());
		mvc.perform(put(blocksUrl("not-a-uuid")).with(auth(a)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unblockDoesNotRestoreMatchAndRequiresFreshMutualLike() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		likeThenMatch(a, b);

		mvc.perform(put(blocksUrl(publicIdOf(b))).with(auth(a))).andExpect(status().isNoContent());
		mvc.perform(delete(blocksUrl(publicIdOf(b))).with(auth(a))).andExpect(status().isNoContent());

		mvc.perform(put("/api/v1/likes/" + publicIdOf(b)).with(auth(a)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liked").value(true))
				.andExpect(jsonPath("$.matchState").value("NONE"));
	}

	@Test
	void reportValidationRejectsBadCategoryTargetAndSelfReports() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");

		mvc.perform(post("/api/v1/reports").with(auth(a)).contentType("application/json")
				.content(reportJson("PROFILE", publicIdOf(b), "NOT_A_CATEGORY", null)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REPORT"));
		mvc.perform(post("/api/v1/reports").with(auth(a)).contentType("application/json")
				.content(reportJson("PROFILE", publicIdOf(b), "SPAM", "x".repeat(501))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REPORT"));
		mvc.perform(post("/api/v1/reports").with(auth(a)).contentType("application/json")
				.content(reportJson("PROFILE", publicIdOf(a), "SPAM", null)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CANNOT_REPORT_SELF"));
		mvc.perform(post("/api/v1/reports").with(auth(a)).contentType("application/json")
				.content(reportJson("PROFILE", UUID.randomUUID().toString(), "SPAM", null)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("REPORT_TARGET_NOT_FOUND"));
	}

	@Test
	void duplicateProfileReportReturnsSameReceiptAndStaysPending() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");

		String first = created(post("/api/v1/reports").with(auth(a)).contentType("application/json")
				.content(reportJson("PROFILE", publicIdOf(b), "HARASSMENT", "Repeated unwanted messages")));
		String second = created(post("/api/v1/reports").with(auth(a)).contentType("application/json")
				.content(reportJson("PROFILE", publicIdOf(b), "HARASSMENT", null)));

		assertEquals("PENDING", JsonPath.read(first, "$.status"));
		assertEquals(JsonPath.<String>read(first, "$.reportId"), JsonPath.<String>read(second, "$.reportId"));
	}

	@Test
	void messageReportRequiresParticipationAndRejectsOwnMessages() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String outsider = eligibleUser("Cy");
		String conversation = matchedConversation(a, b);
		String messageId = JsonPath.read(body(post(messagesUrl(conversation)).with(auth(b))
				.contentType("application/json").content(sendJson(UUID.randomUUID(), "message to report"))),
				"$.messageId");

		mvc.perform(post("/api/v1/reports").with(auth(b)).contentType("application/json")
				.content(reportJson("MESSAGE", messageId, "UNWANTED_CONTENT", null)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("CANNOT_REPORT_OWN_MESSAGE"));
		created(post("/api/v1/reports").with(auth(a)).contentType("application/json")
				.content(reportJson("MESSAGE", messageId, "UNWANTED_CONTENT", null)));
		mvc.perform(post("/api/v1/reports").with(auth(outsider)).contentType("application/json")
				.content(reportJson("MESSAGE", messageId, "SPAM", null)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("REPORT_TARGET_NOT_FOUND"));
	}

	@Test
	void reportsAreNotExposedInDiscoveryOrNotifications() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		created(post("/api/v1/reports").with(auth(a)).contentType("application/json")
				.content(reportJson("PROFILE", publicIdOf(b), "SPAM", null)));

		String discovery = body(get("/api/v1/discovery").with(auth(b)));
		assertFalse(discovery.contains("reportId"));
		assertFalse(discovery.contains("category"));
	}

	@Test
	void matchCreatesMatchNotificationForEachParticipant() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		likeThenMatch(a, b);

		assertEquals(List.of("MATCH"), kindsOf(a));
		assertEquals(List.of("MATCH"), kindsOf(b));
		assertEquals(1, unreadCountOf(a));
	}

	@Test
	void messageNotifiesOnlyRecipientAndRetryDoesNotDuplicate() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		String conversation = matchedConversation(a, b);
		UUID clientId = UUID.randomUUID();

		body(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
				.content(sendJson(clientId, "ping")));
		body(post(messagesUrl(conversation)).with(auth(a)).contentType("application/json")
				.content(sendJson(clientId, "ping")));

		assertEquals(List.of("NEW_MESSAGE", "MATCH"), kindsOf(b));
		assertEquals(List.of("MATCH"), kindsOf(a));
	}

	@Test
	void notificationsAreOwnerOnlyAndReadStateUpdates() throws Exception {
		String a = eligibleUser("Ada");
		String b = eligibleUser("Ben");
		likeThenMatch(a, b);
		String bNotification = JsonPath.read(body(get("/api/v1/notifications").with(auth(b))),
				"$.items[0].notificationId");

		mvc.perform(put("/api/v1/notifications/" + bNotification + "/read").with(auth(a)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

		mvc.perform(put("/api/v1/notifications/" + bNotification + "/read").with(auth(b)))
				.andExpect(status().isNoContent());
		assertEquals(0, unreadCountOf(b));

		mvc.perform(put("/api/v1/notifications/read-all").with(auth(a))).andExpect(status().isNoContent());
		assertEquals(0, unreadCountOf(a));
	}

	@Test
	void unauthenticatedAccessIsRejected() throws Exception {
		mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/reports").contentType("application/json")
				.content(reportJson("PROFILE", UUID.randomUUID().toString(), "SPAM", null)))
				.andExpect(status().isUnauthorized());
	}

	private String matchedConversation(String a, String b) throws Exception {
		likeThenMatch(a, b);
		List<String> ids = JsonPath.read(body(get("/api/v1/conversations?size=50").with(auth(a))),
				"$.items[*].conversationId");
		assertTrue(!ids.isEmpty(), "expected a conversation for matched user");
		return ids.get(0);
	}

	private String likeThenMatch(String a, String b) throws Exception {
		body(put("/api/v1/likes/" + publicIdOf(b)).with(auth(a)));
		return JsonPath.read(body(put("/api/v1/likes/" + publicIdOf(a)).with(auth(b))), "$.matchPublicId");
	}

	private List<String> kindsOf(String sub) throws Exception {
		return JsonPath.read(body(get("/api/v1/notifications?size=50").with(auth(sub))), "$.items[*].kind");
	}

	private int unreadCountOf(String sub) throws Exception {
		return JsonPath.read(body(get("/api/v1/notifications?size=50").with(auth(sub))), "$.unreadCount");
	}

	private List<String> discoveryIds(String sub) throws Exception {
		return JsonPath.read(body(get("/api/v1/discovery").with(auth(sub))), "$.items[*].publicId");
	}

	private static String blocksUrl(String profilePublicId) {
		return "/api/v1/blocks/" + profilePublicId;
	}

	private static String messagesUrl(String conversation) {
		return "/api/v1/conversations/" + conversation + "/messages";
	}

	private static String sendJson(UUID clientMessageId, String body) {
		return "{\"clientMessageId\":\"" + clientMessageId + "\",\"body\":\"" + body + "\"}";
	}

	private static String reportJson(String targetType, String targetId, String category, String details) {
		String detailsField = details == null ? "" : ",\"details\":\"" + details + "\"";
		return "{\"targetType\":\"" + targetType + "\",\"targetId\":\"" + targetId + "\",\"category\":\""
				+ category + "\"" + detailsField + "}";
	}

	private String body(RequestBuilder request) throws Exception {
		return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private String created(RequestBuilder request) throws Exception {
		return mvc.perform(request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
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
