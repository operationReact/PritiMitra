package com.pritimitra.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class Phase7ModerationApiTests {
    private static final String ADULT_DOB = "2000-01-01";
    private static final String MODERATOR_GROUP = "PritiMitraModerators";

    @Autowired private MockMvc mvc;
    @Autowired private LegalProperties legal;
    @Autowired private JdbcClient jdbc;
    @MockitoBean private EmailVerificationChecker emailVerification;

    @Test
    void onlyCognitoModeratorsCanAccessTheQueueOrMakeDecisions() throws Exception {
        String reporter = eligibleUser("Reporter");
        String target = eligibleUser("Target");
        String reportId = createProfileReport(reporter, publicIdOf(target));

        mvc.perform(get("/api/v1/moderation/reports").with(auth(reporter)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODERATOR_REQUIRED"));
        mvc.perform(put("/api/v1/moderation/reports/" + reportId + "/decision").with(auth(reporter))
                .contentType("application/json").content("{\"decision\":\"DISMISS\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODERATOR_REQUIRED"));

        Long pending = jdbc.sql("SELECT count(*) FROM user_report WHERE public_id=:id AND status='PENDING'")
                .param("id", UUID.fromString(reportId)).query(Long.class).single();
        assertEquals(1L, pending);
        assertEquals(0L, auditEvents(reportId));
    }

    @Test
    void moderatorCanHideReportedPostAndDecisionIsAudited() throws Exception {
        String author = eligibleUser("Post author");
        String reporter = eligibleUser("Reporter");
        String groupId = JsonPath.read(body(get("/api/v1/community/groups").with(auth(author))), "$.items[0].publicId");
        mvc.perform(put("/api/v1/community/groups/" + groupId + "/membership").with(auth(author))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/community/groups/" + groupId + "/membership").with(auth(reporter))).andExpect(status().isOk());
        String postResponse = mvc.perform(post("/api/v1/community/groups/" + groupId + "/posts")
                .with(auth(author)).contentType("application/json").content("{\"body\":\"reported safety test post\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String postId = JsonPath.read(postResponse, "$.postPublicId");
        String reportResponse = mvc.perform(post("/api/v1/reports").with(auth(reporter)).contentType("application/json")
                .content(reportJson("COMMUNITY_POST", postId, "UNWANTED_CONTENT")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String reportId = JsonPath.read(reportResponse, "$.reportId");

        mvc.perform(get("/api/v1/moderation/reports?status=PENDING&size=20").with(moderator("moderator-" + UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reportId").value(reportId))
                .andExpect(jsonPath("$.items[0].targetType").value("COMMUNITY_POST"))
                .andExpect(jsonPath("$.items[0].targetPreview").value("reported safety test post"));

        mvc.perform(put("/api/v1/moderation/reports/" + reportId + "/decision")
                .with(moderator("moderator-" + UUID.randomUUID())).contentType("application/json")
                .content("{\"decision\":\"HIDE_POST\",\"note\":\"Removed after review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(reportId))
                .andExpect(jsonPath("$.status").value("ACTIONED"))
                .andExpect(jsonPath("$.decision").value("HIDE_POST"));

        mvc.perform(get("/api/v1/community/groups/" + groupId + "/posts").with(auth(reporter)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        assertEquals(1L, auditEvents(reportId));
        assertEquals("COMMUNITY_POST_HIDDEN", jdbc.sql("SELECT event_type FROM safety_audit_event WHERE report_public_id=:id")
                .param("id", UUID.fromString(reportId)).query(String.class).single());

        mvc.perform(put("/api/v1/moderation/reports/" + reportId + "/decision")
                .with(moderator("moderator-" + UUID.randomUUID())).contentType("application/json")
                .content("{\"decision\":\"DISMISS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_REVIEWED"));
    }

    @Test
    void suspensionRequiresAReasonAndHideActionsMustMatchTheReportedTarget() throws Exception {
        String reporter = eligibleUser("Reporter");
        String target = eligibleUser("Target");
        String reportId = createProfileReport(reporter, publicIdOf(target));
        JwtRequestPostProcessor moderator = moderator("moderator-" + UUID.randomUUID());

        mvc.perform(put("/api/v1/moderation/reports/" + reportId + "/decision").with(moderator)
                .contentType("application/json").content("{\"decision\":\"HIDE_POST\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MODERATION_DECISION"));
        mvc.perform(put("/api/v1/moderation/reports/" + reportId + "/decision").with(moderator)
                .contentType("application/json").content("{\"decision\":\"SUSPEND_ACCOUNT\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MODERATION_DECISION"));

        mvc.perform(put("/api/v1/moderation/reports/" + reportId + "/decision").with(moderator)
                .contentType("application/json").content("{\"decision\":\"SUSPEND_ACCOUNT\",\"note\":\"Repeated targeted harassment\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIONED"));
        assertEquals("SUSPENDED", jdbc.sql("SELECT status FROM app_user WHERE cognito_sub=:sub").param("sub", target).query(String.class).single());
        assertEquals("ACCOUNT_SUSPENDED", jdbc.sql("SELECT event_type FROM safety_audit_event WHERE report_public_id=:id")
                .param("id", UUID.fromString(reportId)).query(String.class).single());
    }

    private String createProfileReport(String reporter, String targetProfileId) throws Exception {
        String response = mvc.perform(post("/api/v1/reports").with(auth(reporter)).contentType("application/json")
                .content(reportJson("PROFILE", targetProfileId, "HARASSMENT")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.reportId");
    }

    private long auditEvents(String reportId) {
        return jdbc.sql("SELECT count(*) FROM safety_audit_event WHERE report_public_id=:id")
                .param("id", UUID.fromString(reportId)).query(Long.class).single();
    }

    private String publicIdOf(String sub) throws Exception {
        return JsonPath.read(body(get("/api/v1/profile").with(auth(sub))), "$.publicId");
    }

    private String body(RequestBuilder request) throws Exception {
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String eligibleUser(String name) throws Exception {
        String sub = "phase7-" + UUID.randomUUID();
        when(emailVerification.isEmailVerified(anyString())).thenReturn(true);
        mvc.perform(put("/api/v1/onboarding/age-verification").with(auth(sub)).contentType("application/json")
                .content("{\"dateOfBirth\":\""+ADULT_DOB+"\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/onboarding/consents").with(auth(sub)).contentType("application/json").content("""
                {"consents":[{"type":"TERMS_OF_SERVICE","policyVersion":"%s","accepted":true},
                             {"type":"PRIVACY_NOTICE","policyVersion":"%s","accepted":true}]}
                """.formatted(legal.termsVersion(), legal.privacyVersion()))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/onboarding/profile").with(auth(sub)).contentType("application/json")
                .content("{\"displayName\":\""+name+"\",\"goals\":[\"FRIENDSHIP\"]}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/onboarding/complete").with(auth(sub))).andExpect(status().isOk());
        return sub;
    }

    private static String reportJson(String type, String targetId, String category) {
        return "{\"targetType\":\""+type+"\",\"targetId\":\""+targetId+"\",\"category\":\""+category+"\"}";
    }

    private static JwtRequestPostProcessor auth(String sub) {
        return jwt().jwt(Jwt.withTokenValue("token-" + sub).header("alg", "RS256").subject(sub)
                .claim("token_use", "access").build()).authorities();
    }

    private static JwtRequestPostProcessor moderator(String sub) {
        return jwt().jwt(Jwt.withTokenValue("token-" + sub).header("alg", "RS256").subject(sub)
                .claim("token_use", "access").claim("cognito:groups", List.of(MODERATOR_GROUP)).build()).authorities();
    }
}
