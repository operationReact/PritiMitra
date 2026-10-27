package com.pritimitra.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ModerationApiTests {
    private static final String ADULT_DOB = "2000-01-01";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private LegalProperties legal;
    @Autowired
    private JdbcClient jdbc;
    @MockitoBean
    private EmailVerificationChecker emailVerification;

    @Test
    void queueRequiresModeratorGroupAndSuspensionIsAuditedAndIdempotencyIsProtected() throws Exception {
        String reporter = eligibleUser("Reporter");
        String target = eligibleUser("Reported");
        String moderator = eligibleUser("Moderator");

        String createdReport = mvc.perform(post("/api/v1/reports").with(auth(reporter)).contentType("application/json")
                .content("{\"targetType\":\"PROFILE\",\"targetId\":\"" + publicIdOf(target)
                        + "\",\"category\":\"HARASSMENT\",\"details\":\"Repeated unwanted contact\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String reportId = JsonPath.read(createdReport, "$.reportId");

        mvc.perform(get("/api/v1/moderation/reports?status=PENDING&size=20").with(auth(moderator)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODERATION_ACCESS_DENIED"));

        mvc.perform(get("/api/v1/moderation/reports?status=PENDING&size=20").with(moderatorAuth(moderator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reportId").value(reportId))
                .andExpect(jsonPath("$.items[0].category").value("HARASSMENT"))
                .andExpect(jsonPath("$.items[0].details").value("Repeated unwanted contact"))
                .andExpect(jsonPath("$.items[0].reportedDisplayName").value("Reported"));

        mvc.perform(put("/api/v1/moderation/reports/" + reportId).with(moderatorAuth(moderator))
                .contentType("application/json")
                .content("{\"decision\":\"SUSPEND_ACCOUNT\",\"note\":\"Repeated unwanted contact\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIONED"))
                .andExpect(jsonPath("$.action").value("SUSPEND_ACCOUNT"));

        assertEquals("SUSPENDED", jdbc.sql("SELECT status FROM app_user WHERE cognito_sub = :sub")
                .param("sub", target).query(String.class).single());
        assertFalse(jdbc.sql("SELECT discovery_visible FROM onboarding_profile WHERE user_id = "
                + "(SELECT id FROM app_user WHERE cognito_sub = :sub)")
                .param("sub", target).query(Boolean.class).single());
        assertEquals(1L, jdbc.sql("SELECT count(*) FROM moderation_audit_event WHERE action='ACCOUNT_SUSPENDED'")
                .query(Long.class).single());

        mvc.perform(put("/api/v1/moderation/reports/" + reportId).with(moderatorAuth(moderator))
                .contentType("application/json").content("{\"decision\":\"DISMISS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_REVIEWED"));
    }

    @Test
    void invalidStatusAndCursorAreRejected() throws Exception {
        String moderator = eligibleUser("Moderator");
        mvc.perform(get("/api/v1/moderation/reports?status=UNKNOWN").with(moderatorAuth(moderator)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_STATUS"));
        mvc.perform(get("/api/v1/moderation/reports?cursor=not-a-valid-cursor!!").with(moderatorAuth(moderator)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    private String publicIdOf(String sub) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/v1/profile").with(auth(sub))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.publicId");
    }

    private String eligibleUser(String displayName) throws Exception {
        String sub = "sub-" + UUID.randomUUID();
        when(emailVerification.isEmailVerified(anyString())).thenReturn(true);
        mvc.perform(put("/api/v1/onboarding/age-verification").with(auth(sub)).contentType("application/json")
                .content("{\"dateOfBirth\":\"" + ADULT_DOB + "\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/onboarding/consents").with(auth(sub)).contentType("application/json")
                .content("""
                        {"consents":[
                          {"type":"TERMS_OF_SERVICE","policyVersion":"%s","accepted":true},
                          {"type":"PRIVACY_NOTICE","policyVersion":"%s","accepted":true}]}
                        """.formatted(legal.termsVersion(), legal.privacyVersion()))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/onboarding/profile").with(auth(sub)).contentType("application/json")
                .content("{\"displayName\":\"" + displayName + "\",\"goals\":[\"FRIENDSHIP\"]}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/onboarding/complete").with(auth(sub))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/profile/discoverability").with(auth(sub)).contentType("application/json")
                .content("{\"discoverable\":true}")).andExpect(status().isOk());
        return sub;
    }

    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor auth(String sub) {
        return jwt().jwt(Jwt.withTokenValue("token-" + sub).header("alg", "RS256").subject(sub)
                .claim("token_use", "access").build()).authorities();
    }

    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor moderatorAuth(String sub) {
        return jwt().jwt(Jwt.withTokenValue("token-" + sub).header("alg", "RS256").subject(sub)
                .claim("token_use", "access").claim("cognito:groups", List.of("PRITIMITRA_MODERATORS")).build())
                .authorities();
    }
}
