package com.pritimitra.backend.preferences;

import jakarta.validation.constraints.NotNull;

final class DesignExperienceDtos {

	private DesignExperienceDtos() {
	}

	/** Wire values are lowercase to match the Flutter and web clients. */
	record DesignExperienceResponse(String experience) {
	}

	record UpdateDesignExperienceRequest(@NotNull String experience) {
	}
}
