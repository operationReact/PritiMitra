package com.pritimitra.backend.onboarding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only consent history. Rows are never updated or deleted (enforced by a database trigger). */
@Entity
@Table(name = "consent_record")
class ConsentRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "consent_type", nullable = false, updatable = false, length = 32)
	private ConsentType consentType;

	@Column(name = "policy_version", nullable = false, updatable = false, length = 20)
	private String policyVersion;

	@Column(nullable = false, updatable = false)
	private boolean accepted;

	@Column(name = "recorded_at", nullable = false, updatable = false)
	private Instant recordedAt;

	protected ConsentRecord() {
	}

	ConsentRecord(UUID userId, ConsentType consentType, String policyVersion, boolean accepted, Instant recordedAt) {
		this.userId = userId;
		this.consentType = consentType;
		this.policyVersion = policyVersion;
		this.accepted = accepted;
		this.recordedAt = recordedAt;
	}
}
