package com.pritimitra.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifies V6 reconciles the profile photo content-type constraint on a clean schema, on an upgrade from V5,
 * and fails closed when WebP rows exist. Each test uses an isolated schema.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProfilePhotoMigrationTests {

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
	void cleanSchemaAcceptsOnlyJpegAndPng() {
		migrate("latest");
		var user = insertUser();

		insertPhoto(user, "image/jpeg");
		insertPhoto(user, "image/png");
		assertThatThrownBy(() -> insertPhoto(user, "image/webp"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void upgradeFromV5KeepsExistingJpegAndPngRowsAndRejectsWebp() {
		migrate("5");
		var user = insertUser();
		insertPhoto(user, "image/jpeg");
		insertPhoto(user, "image/png");

		migrate("latest");

		Integer rows = jdbc.queryForObject(
				"select count(*) from \"%s\".profile_photo".formatted(schema), Integer.class);
		assertThat(rows).isEqualTo(2);
		assertThatThrownBy(() -> insertPhoto(user, "image/webp"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void upgradeFailsClosedWhenWebpRowsExist() {
		migrate("5");
		var user = insertUser();
		insertPhoto(user, "image/webp");

		assertThatThrownBy(() -> migrate("latest"))
				.hasMessageContaining("unsupported content types");

		String version = jdbc.queryForObject(
				"select max(version) from \"%s\".flyway_schema_history where success".formatted(schema),
				String.class);
		assertThat(version).isEqualTo("5");
	}

	private void migrate(String target) {
		if (schema == null) {
			schema = "photo_" + UUID.randomUUID().toString().replace("-", "");
		}
		Flyway.configure()
				.dataSource(dataSource)
				.schemas(schema)
				.createSchemas(true)
				.target(target)
				.load()
				.migrate();
	}

	private UUID insertUser() {
		var id = UUID.randomUUID();
		jdbc.update("insert into \"%s\".app_user (id, cognito_sub, status) values (?, ?, 'ACTIVE')".formatted(schema),
				id, "photo-migration-" + id);
		return id;
	}

	private void insertPhoto(UUID userId, String contentType) {
		jdbc.update("""
				insert into "%s".profile_photo (user_id, storage_key, content_type, size_bytes)
				values (?, ?, ?, 1024)
				""".formatted(schema), userId, "profiles/" + UUID.randomUUID(), contentType);
	}
}
