package com.pritimitra.backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Versions of the terms and privacy notice that users must accept. Consent is recorded against these values,
 * so changing a version requires users to accept again.
 */
@Validated
@ConfigurationProperties("pritimitra.legal")
public record LegalProperties(@NotBlank @Size(max = 40) String termsVersion,
		@NotBlank @Size(max = 40) String privacyVersion,
		@Size(max = 500) String termsUrl,
		@Size(max = 500) String privacyUrl,
		boolean requireApproved) {

	private static final String DRAFT_MARKER = "draft";

	public LegalProperties {
		if (requireApproved) {
			requireApprovedVersion("terms-version", termsVersion);
			requireApprovedVersion("privacy-version", privacyVersion);
			requireUrl("terms-url", termsUrl);
			requireUrl("privacy-url", privacyUrl);
		}
	}

	private static void requireApprovedVersion(String name, String version) {
		if (version.toLowerCase(java.util.Locale.ROOT).contains(DRAFT_MARKER)) {
			throw new IllegalStateException("pritimitra.legal." + name
					+ " must be an approved policy version when requireApproved is enabled");
		}
	}

	private static void requireUrl(String name, String url) {
		if (url == null || !url.startsWith("https://")) {
			throw new IllegalStateException("pritimitra.legal." + name
					+ " must be an https URL when requireApproved is enabled");
		}
	}
}
