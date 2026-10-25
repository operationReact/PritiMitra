package com.pritimitra.backend;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pritimitra.backend.identity.CognitoUserAdministration;
import com.pritimitra.backend.identity.EmailVerificationChecker;
import com.pritimitra.backend.identity.IneligibleAccountPurgeService;
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

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class IneligibleAccountPurgeTests {

	private static final String MINOR_DOB = java.time.LocalDate.now().minusYears(15).toString();

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcClient jdbc;

	@Autowired
	private IneligibleAccountPurgeService purgeService;

	@MockitoBean
	private EmailVerificationChecker emailVerification;

	@MockitoBean
	private CognitoUserAdministration cognito;

	@Test
	void purgesIneligibleAccountsPastRetentionAndDeletesTheCognitoIdentity() throws Exception {
		String expiredSub = registerUnderage();
		String freshSub = registerUnderage();
		jdbc.sql("update app_user set ineligible_at = now() - interval '60 days' where cognito_sub = :sub")
				.param("sub", expiredSub).update();

		purgeService.purgeExpiredIneligibleAccounts();

		verify(cognito).deleteUser(expiredSub);
		assertRowCount(expiredSub, 0);
		assertRowCount(freshSub, 1);
	}

	@Test
	void purgeLeavesActiveAccountsUntouched() throws Exception {
		String sub = "sub-" + UUID.randomUUID();
		jdbc.sql("insert into app_user (id, cognito_sub, status, created_at, updated_at) "
				+ "values (:id, :sub, 'ACTIVE', now(), now())")
				.param("id", UUID.randomUUID()).param("sub", sub).update();

		purgeService.purgeExpiredIneligibleAccounts();

		assertRowCount(sub, 1);
	}

	private String registerUnderage() throws Exception {
		String sub = "sub-" + UUID.randomUUID();
		when(emailVerification.isEmailVerified(anyString())).thenReturn(true);
		mvc.perform(put("/api/v1/onboarding/age-verification").with(auth(sub)).contentType("application/json")
				.content("{\"dateOfBirth\":\"" + MINOR_DOB + "\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("AGE_INELIGIBLE"));
		return sub;
	}

	private void assertRowCount(String sub, int expected) {
		Integer count = jdbc.sql("select count(*) from app_user where cognito_sub = :sub")
				.param("sub", sub).query(Integer.class).single();
		org.assertj.core.api.Assertions.assertThat(count).isEqualTo(expected);
	}

	private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor auth(String sub) {
		return jwt().jwt(Jwt.withTokenValue("token-" + sub).header("alg", "RS256").subject(sub)
				.claim("token_use", "access").build()).authorities();
	}
}
