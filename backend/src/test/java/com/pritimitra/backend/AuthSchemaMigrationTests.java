package com.pritimitra.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuthSchemaMigrationTests {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void v3MigrationIsApplied() {
		Integer count = jdbc.queryForObject(
				"select count(*) from flyway_schema_history where version = '3' and success", Integer.class);
		assertThat(count).isEqualTo(1);
	}

	@Test
	void legacyCredentialTablesAreRemoved() {
		Integer count = jdbc.queryForObject("""
				select count(*) from information_schema.tables
				where table_schema = current_schema() and table_name in ('refresh_session', 'email_challenge')
				""", Integer.class);
		assertThat(count).isZero();
	}

	@Test
	void cognitoSubjectIsUnique() {
		String sub = "sub-" + System.nanoTime();
		insertUser(sub, "PENDING_EMAIL_VERIFICATION");
		assertThatThrownBy(() -> insertUser(sub, "PENDING_EMAIL_VERIFICATION"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsUnknownUserStatus() {
		assertThatThrownBy(() -> insertUser("status-" + System.nanoTime(), "BANNED"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void consentRecordsAreAppendOnly() {
		var userId = insertUser("consent-" + System.nanoTime(), "ACTIVE");
		jdbc.update("insert into consent_record (user_id, consent_type, policy_version, accepted) values (?, 'TERMS_OF_SERVICE', '2026-01', true)",
				userId);

		assertThatThrownBy(() -> jdbc.update("update consent_record set accepted = false where user_id = ?", userId))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("append-only");
		assertThatThrownBy(() -> jdbc.update("delete from consent_record where user_id = ?", userId))
				.isInstanceOf(DataAccessException.class)
				.hasMessageContaining("append-only");
	}

	@Test
	void rejectsDiscoveryAgeRangeInverted() {
		var userId = insertUser("range-" + System.nanoTime(), "ACTIVE");
		assertThatThrownBy(() -> jdbc.update(
				"insert into onboarding_profile (user_id, discovery_min_age, discovery_max_age) values (?, 40, 25)", userId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void sensitiveVisibilityDefaultsToHidden() {
		var userId = insertUser("defaults-" + System.nanoTime(), "ACTIVE");
		jdbc.update("insert into onboarding_profile (user_id) values (?)", userId);
		Boolean pronounsVisible = jdbc.queryForObject(
				"select pronouns_visible from onboarding_profile where user_id = ?", Boolean.class, userId);
		Boolean orientationVisible = jdbc.queryForObject(
				"select sexual_orientation_visible from onboarding_profile where user_id = ?", Boolean.class, userId);
		assertThat(pronounsVisible).isFalse();
		assertThat(orientationVisible).isFalse();
	}

	private Object insertUser(String cognitoSub, String status) {
		var id = java.util.UUID.randomUUID();
		jdbc.update("insert into app_user (id, cognito_sub, status) values (?, ?, ?)", id, cognitoSub, status);
		return id;
	}
}
