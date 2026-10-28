package com.pritimitra.backend;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pritimitra.backend.identity.AccountDeletionService;
import com.pritimitra.backend.identity.CognitoUserAdministration;
import com.pritimitra.backend.photos.PhotoStorage;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "pritimitra.account-deletion.poll-interval-ms=3600000")
@AutoConfigureMockMvc
class AccountLifecycleApiTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private AccountDeletionService deletions;

    @MockitoBean
    private CognitoUserAdministration cognito;

    @MockitoBean
    private PhotoStorage photoStorage;

    @Test
    void optionalConsentCanBeGrantedAndWithdrawnWithoutMutatingHistory() throws Exception {
        String sub = createActiveUser();

        mvc.perform(get("/api/v1/account/consents").with(auth(sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.communityNicknameAccepted").value(false));

        mvc.perform(put("/api/v1/account/consents/community-nickname").with(auth(sub))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accepted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.communityNicknameAccepted").value(true));

        mvc.perform(put("/api/v1/account/consents/community-nickname").with(auth(sub))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accepted\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.communityNicknameAccepted").value(false));

        UUID userId = jdbc.sql("SELECT id FROM app_user WHERE cognito_sub = :sub")
                .param("sub", sub).query(UUID.class).single();
        assert jdbc.sql("""
                SELECT count(*) FROM consent_record
                WHERE user_id = :uid AND consent_type = 'COMMUNITY_NICKNAME'
                """).param("uid", userId).query(Long.class).single() == 2L;
        assert jdbc.sql("""
                SELECT count(*) FROM account_lifecycle_audit_event
                WHERE subject_digest = (SELECT subject_digest FROM account_deletion_request WHERE subject_digest = :digest)
                """).param("digest", "not-a-deletion-digest").query(Long.class).single() == 0L;
    }

    @Test
    void deletionIsQueuedThenRemovesIdentityDataAndKeepsOnlyPrivacySafeTombstone() throws Exception {
        String sub = createActiveUser();
        UUID userId = jdbc.sql("SELECT id FROM app_user WHERE cognito_sub = :sub")
                .param("sub", sub).query(UUID.class).single();
        jdbc.sql("""
                INSERT INTO consent_record (user_id, consent_type, policy_version, accepted)
                VALUES (:uid, 'COMMUNITY_NICKNAME', 'community-nickname-v1', true)
                """).param("uid", userId).update();
        jdbc.sql("""
                INSERT INTO community_post (group_id, author_id, body)
                SELECT id, :uid, 'private profile detail in post' FROM community_group
                ORDER BY id LIMIT 1
                """).param("uid", userId).update();

        mvc.perform(delete("/api/v1/account").with(auth(sub)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assert jdbc.sql("SELECT status FROM app_user WHERE cognito_sub = :sub")
                .param("sub", sub).query(String.class).single().equals("DELETION_PENDING");

        deletions.processPendingDeletions();
        verify(cognito).deleteUser(sub);

        assert jdbc.sql("SELECT count(*) FROM app_user WHERE cognito_sub = :sub")
                .param("sub", sub).query(Long.class).single() == 0L;
        assert jdbc.sql("SELECT count(*) FROM consent_record WHERE user_id = :uid")
                .param("uid", userId).query(Long.class).single() == 0L;
        assert jdbc.sql("""
                SELECT count(*) FROM community_post
                WHERE author_id IS NULL AND body = '[removed by account owner]' AND status = 'REMOVED'
                """).query(Long.class).single() >= 1L;
        assert jdbc.sql("""
                SELECT count(*) FROM account_deletion_request
                WHERE subject_digest IS NOT NULL AND cognito_sub IS NULL AND status = 'COMPLETED'
                  AND completed_at IS NOT NULL
                """).query(Long.class).single() >= 1L;
        assert jdbc.sql("""
                SELECT count(*) FROM account_lifecycle_audit_event
                WHERE subject_digest = (SELECT subject_digest FROM account_deletion_request
                                        WHERE subject_digest IS NOT NULL AND cognito_sub IS NULL
                                        ORDER BY completed_at DESC LIMIT 1)
                  AND event_type = 'ACCOUNT_DELETION_COMPLETED'
                """).query(Long.class).single() >= 1L;

        mvc.perform(get("/api/v1/me").with(auth(sub)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DELETION_PENDING"));
    }

    private String createActiveUser() {
        String sub = "lifecycle-" + UUID.randomUUID();
        jdbc.sql("INSERT INTO app_user (cognito_sub, status) VALUES (:sub, 'ACTIVE')")
                .param("sub", sub).update();
        return sub;
    }

    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor auth(String sub) {
        return jwt().jwt(Jwt.withTokenValue("token-" + sub).header("alg", "RS256").subject(sub)
                .claim("token_use", "access").build()).authorities();
    }
}
