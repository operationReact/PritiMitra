package com.pritimitra.backend.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Retention limits. Underage registration attempts keep only the account reference and status, and are purged
 * (including the Cognito identity) once this many days have passed since the ineligibility decision.
 */
@Validated
@ConfigurationProperties("pritimitra.retention")
public record RetentionProperties(@Min(1) int ineligibleAccountDays) {
}
