package com.pritimitra.backend.preferences;

import com.pritimitra.backend.common.ApiException;
import com.pritimitra.backend.preferences.DesignExperienceDtos.DesignExperienceResponse;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owner-only read and write of the design experience. Unknown values are rejected, never stored. */
@Service
class DesignExperienceService {

	static final String DEFAULT_EXPERIENCE = "spark";
	private static final Set<String> ALLOWED = Set.of("spark", "essence");

	private final JdbcClient jdbc;

	DesignExperienceService(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	DesignExperienceResponse get(String cognitoSub) {
		UUID userId = activeUserId(cognitoSub);
		String value = jdbc.sql("SELECT experience FROM user_design_experience WHERE user_id = :uid")
				.param("uid", userId)
				.query(String.class)
				.optional()
				.orElse(DEFAULT_EXPERIENCE);
		return new DesignExperienceResponse(value);
	}

	@Transactional
	DesignExperienceResponse update(String cognitoSub, String experience) {
		String normalized = experience == null ? "" : experience.trim().toLowerCase(Locale.ROOT);
		if (!ALLOWED.contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DESIGN_EXPERIENCE",
					"Design experience must be one of: spark, essence");
		}
		UUID userId = activeUserId(cognitoSub);
		jdbc.sql("""
				INSERT INTO user_design_experience (user_id, experience, updated_at)
				VALUES (:uid, :experience, now())
				ON CONFLICT (user_id) DO UPDATE SET experience = EXCLUDED.experience, updated_at = now()
				""")
				.param("uid", userId)
				.param("experience", normalized)
				.update();
		return new DesignExperienceResponse(normalized);
	}

	private UUID activeUserId(String cognitoSub) {
		return jdbc.sql("SELECT id FROM app_user WHERE cognito_sub = :sub AND status = 'ACTIVE'")
				.param("sub", cognitoSub)
				.query(UUID.class)
				.optional()
				.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ELIGIBLE",
						"Design preferences are available only to active accounts"));
	}
}
