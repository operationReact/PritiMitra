package com.pritimitra.backend;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

import com.jayway.jsonpath.JsonPath;
import com.pritimitra.backend.config.LegalProperties;
import com.pritimitra.backend.identity.EmailVerificationChecker;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
class Phase6ApiTests {
    private static final String ADULT_DOB = "2000-01-01";
    @Autowired private MockMvc mvc;
    @Autowired private LegalProperties legal;
    @MockitoBean private EmailVerificationChecker emailVerification;

    @Test
    void communityMembershipPostsReactionsAndPrivateReports() throws Exception {
        String ada=eligibleUser("Ada"), ben=eligibleUser("Ben");
        String groupId=JsonPath.read(body(get("/api/v1/community/groups").with(auth(ada))),"$.items[0].publicId");
        mvc.perform(post("/api/v1/community/groups/"+groupId+"/posts").with(auth(ada)).contentType("application/json").content("{\"body\":\"hello community\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/community/groups/"+groupId+"/membership").with(auth(ada))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/community/groups/"+groupId+"/membership").with(auth(ben))).andExpect(status().isOk());
        String created=mvc.perform(post("/api/v1/community/groups/"+groupId+"/posts").with(auth(ada)).contentType("application/json")
                .content("{\"body\":\"hello community\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String postId=JsonPath.read(created,"$.postPublicId");
        mvc.perform(put("/api/v1/community/posts/"+postId+"/reaction").with(auth(ben)).contentType("application/json").content("{\"reaction\":\"HEART\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.reaction").value("HEART"));
        mvc.perform(post("/api/v1/reports").with(auth(ben)).contentType("application/json")
                .content("{\"targetType\":\"COMMUNITY_POST\",\"targetId\":\""+postId+"\",\"category\":\"UNWANTED_CONTENT\",\"details\":\"please review\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get("/api/v1/community/groups/"+groupId+"/posts?size=10").with(auth(ada)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].postPublicId").value(postId))
            .andExpect(jsonPath("$.items[0].heartCount").value(1)).andExpect(jsonPath("$.items[0].mine").value(true));
        mvc.perform(delete("/api/v1/community/posts/"+postId).with(auth(ada))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/community/groups/"+groupId+"/posts").with(auth(ben))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void blindDatePairsOnlyIndependentOptInsForSameSlotAndCanBeCancelled() throws Exception {
        String ada=eligibleUser("Ada"), ben=eligibleUser("Ben");
        long epoch=((Instant.now().getEpochSecond()/900)+5)*900;
        String startsAt=OffsetDateTime.ofInstant(Instant.ofEpochSecond(epoch),ZoneOffset.UTC).toString();
        String adaProfile=JsonPath.read(body(get("/api/v1/profile").with(auth(ada))),"$.publicId");
        String first=mvc.perform(post("/api/v1/blind-dates/opt-ins").with(auth(ada)).contentType("application/json")
                .content("{\"startsAt\":\""+startsAt+"\",\"timeZone\":\"UTC\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING")).andReturn().getResponse().getContentAsString();
        String optInId=JsonPath.read(first,"$.optInId");
        String second=mvc.perform(post("/api/v1/blind-dates/opt-ins").with(auth(ben)).contentType("application/json")
                .content("{\"startsAt\":\""+startsAt+"\",\"timeZone\":\"UTC\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAIRED")).andExpect(jsonPath("$.partnerProfilePublicId").value(adaProfile))
                .andReturn().getResponse().getContentAsString();
        String sessionId=JsonPath.read(second,"$.sessionId");
        mvc.perform(delete("/api/v1/blind-dates/sessions/"+sessionId).with(auth(ben))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/blind-dates/my").with(auth(ada))).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].status").value("WITHDRAWN"))
            .andExpect(jsonPath("$.items[0].sessionStatus").value("CANCELLED"))
            .andExpect(jsonPath("$.items[0].partnerProfilePublicId").value(nullValue()));
        mvc.perform(delete("/api/v1/blind-dates/opt-ins/"+optInId).with(auth(ada))).andExpect(status().isNoContent());
    }

    @Test
    void blindDateRejectsBadSlots() throws Exception {
        String ada=eligibleUser("Ada");
        String bad=OffsetDateTime.now(ZoneOffset.UTC).plusHours(2).withMinute(7).withSecond(0).withNano(0).toString();
        mvc.perform(post("/api/v1/blind-dates/opt-ins").with(auth(ada)).contentType("application/json")
                .content("{\"startsAt\":\""+bad+"\",\"timeZone\":\"UTC\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_BLIND_DATE_SLOT"));
        String tooFar=OffsetDateTime.now(ZoneOffset.UTC).plusDays(20).withMinute(0).withSecond(0).withNano(0).toString();
        mvc.perform(post("/api/v1/blind-dates/opt-ins").with(auth(ada)).contentType("application/json")
                .content("{\"startsAt\":\""+tooFar+"\",\"timeZone\":\"UTC\"}")).andExpect(status().isBadRequest());
    }

    private String body(RequestBuilder request) throws Exception {
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    private String eligibleUser(String name) throws Exception {
        String sub="phase6-"+UUID.randomUUID(); when(emailVerification.isEmailVerified(anyString())).thenReturn(true);
        mvc.perform(put("/api/v1/onboarding/age-verification").with(auth(sub)).contentType("application/json")
                .content("{\"dateOfBirth\":\""+ADULT_DOB+"\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/onboarding/consents").with(auth(sub)).contentType("application/json").content("""
          {"consents":[{"type":"TERMS_OF_SERVICE","policyVersion":"%s","accepted":true},
                       {"type":"PRIVACY_NOTICE","policyVersion":"%s","accepted":true}]}
          """.formatted(legal.termsVersion(),legal.privacyVersion()))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/onboarding/profile").with(auth(sub)).contentType("application/json")
                .content("{\"displayName\":\""+name+"\",\"goals\":[\"FRIENDSHIP\"]}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/onboarding/complete").with(auth(sub))).andExpect(status().isOk());
        return sub;
    }
    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor auth(String sub) {
        return jwt().jwt(Jwt.withTokenValue("token-"+sub).header("alg","RS256").subject(sub).claim("token_use","access").build()).authorities();
    }
}
