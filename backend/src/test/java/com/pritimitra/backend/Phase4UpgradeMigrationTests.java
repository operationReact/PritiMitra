package com.pritimitra.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Upgrade-path check: rows created at schema V3 (before Phase 4) must survive V4 and V5 and receive
 * private defaults. Uses an isolated schema so the shared test database is not affected.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class Phase4UpgradeMigrationTests {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbc;

	private String schema;

	@AfterEach
	void dropSchema() {
		if (schema != null) {
			jdbc.execute("drop schema if exists \"" + schema + "\" cascade");
		}
	}

	@Test
	void existingProfilesGainPrivateDefaultsAndDistinctPublicIds() {
		schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");

		Flyway.configure()
				.dataSource(dataSource)
				.schemas(schema)
				.createSchemas(true)
				.target("3")
				.load()
				.migrate();

		var first = UUID.randomUUID();
		var second = UUID.randomUUID();
		jdbc.update("insert into \"%s\".app_user (id, cognito_sub, status) values (?, ?, 'ACTIVE')".formatted(schema),
				first, "upgrade-a-" + first);
		jdbc.update("insert into \"%s\".app_user (id, cognito_sub, status) values (?, ?, 'ACTIVE')".formatted(schema),
				second, "upgrade-b-" + second);
		jdbc.update("insert into \"%s\".onboarding_profile (user_id) values (?), (?)".formatted(schema), first, second);

		Flyway.configure()
				.dataSource(dataSource)
				.schemas(schema)
				.target("latest")
				.load()
				.migrate();

		var rows = jdbc.queryForList(
				"select user_id, discovery_visible, public_id from \"%s\".onboarding_profile".formatted(schema));
		assertThat(rows).hasSize(2);
		assertThat(rows).allSatisfy(row -> {
			assertThat(row.get("discovery_visible")).isEqualTo(false);
			assertThat(row.get("public_id")).isNotNull();
		});
		assertThat(rows.stream().map(row -> row.get("public_id")).distinct()).hasSize(2);

		Integer photoTable = jdbc.queryForObject("""
				select count(*) from information_schema.tables
				where table_schema = ? and table_name = 'profile_photo'
				""", Integer.class, schema);
		assertThat(photoTable).isEqualTo(1);
	}
}
