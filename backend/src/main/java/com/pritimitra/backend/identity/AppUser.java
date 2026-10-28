package com.pritimitra.backend.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Application account keyed by the immutable Cognito subject. Credentials live only in Cognito. */
@Entity
@Table(name = "app_user")
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "cognito_sub", nullable = false, updatable = false, unique = true, length = 128)
	private String cognitoSub;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private AccountStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "ineligible_at")
	private Instant ineligibleAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected AppUser() {
	}

	AppUser(String cognitoSub, Instant now) {
		this.cognitoSub = cognitoSub;
		this.status = AccountStatus.PENDING_EMAIL_VERIFICATION;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public String getCognitoSub() {
		return cognitoSub;
	}

	public AccountStatus getStatus() {
		return status;
	}

	public Instant getIneligibleAt() {
		return ineligibleAt;
	}

	public void markEmailVerified(Instant now) {
		if (status == AccountStatus.PENDING_EMAIL_VERIFICATION) {
			this.status = AccountStatus.ONBOARDING_INCOMPLETE;
			this.updatedAt = now;
		}
	}

	public void markIneligible(Instant now) {
		this.status = AccountStatus.INELIGIBLE;
		this.ineligibleAt = now;
		this.updatedAt = now;
	}

	public void activate(Instant now) {
		this.status = AccountStatus.ACTIVE;
		this.updatedAt = now;
	}

	public void markDeletionPending(Instant now) {
		this.status = AccountStatus.DELETION_PENDING;
		this.updatedAt = now;
	}
}
