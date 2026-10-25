package com.pritimitra.backend;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.pritimitra.backend.config.LegalProperties;
import org.junit.jupiter.api.Test;

class LegalPropertiesTests {

	@Test
	void approvedRequirementRejectsDraftVersions() {
		assertThatThrownBy(() -> new LegalProperties("terms-draft-v1", "privacy-v1",
				"https://pritimitra.com/terms", "https://pritimitra.com/privacy", true))
				.hasMessageContaining("terms-version");
	}

	@Test
	void approvedRequirementRejectsNonHttpsUrls() {
		assertThatThrownBy(() -> new LegalProperties("terms-v1", "privacy-v1",
				"http://pritimitra.com/terms", "https://pritimitra.com/privacy", true))
				.hasMessageContaining("terms-url");
	}

	@Test
	void approvedRequirementAcceptsApprovedVersionsAndHttpsUrls() {
		assertThatCode(() -> new LegalProperties("terms-v1", "privacy-v1",
				"https://pritimitra.com/terms", "https://pritimitra.com/privacy", true))
				.doesNotThrowAnyException();
	}

	@Test
	void draftVersionsAreAllowedOnlyWhenApprovalIsNotRequired() {
		assertThatCode(() -> new LegalProperties("terms-draft-v1", "privacy-draft-v1", null, null, false))
				.doesNotThrowAnyException();
	}
}
