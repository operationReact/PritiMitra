package com.pritimitra.backend.identity;

import com.pritimitra.backend.common.ApiException;
import com.pritimitra.backend.photos.PhotoStorage;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account deletion is queued first, then processed idempotently by a worker. Cognito and private S3 objects are
 * removed before the application row; failures leave the request pending for a later retry. Completed requests
 * retain only a SHA-256 subject tombstone and timestamps.
 */
@Service
public class AccountDeletionService {

    private static final Logger LOG = LoggerFactory.getLogger(AccountDeletionService.class);

    private final JdbcClient jdbc;
    private final AppUserRepository users;
    private final CognitoUserAdministration cognito;
    private final PhotoStorage photoStorage;
    private final AccountDeletionRegistry registry;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public AccountDeletionService(JdbcClient jdbc, AppUserRepository users, CognitoUserAdministration cognito,
            PhotoStorage photoStorage, AccountDeletionRegistry registry, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.users = users;
        this.cognito = cognito;
        this.photoStorage = photoStorage;
        this.registry = registry;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public AccountDeletionResponse requestDeletion(String cognitoSub) {
        String digest = registry.digest(cognitoSub);
        ExistingRequest existing = jdbc.sql("""
                SELECT id, status FROM account_deletion_request WHERE subject_digest = :digest
                """).param("digest", digest)
                .query((rs, row) -> new ExistingRequest(rs.getObject("id", UUID.class), rs.getString("status")))
                .optional().orElse(null);
        if (existing != null) {
            return new AccountDeletionResponse(existing.id(), existing.status());
        }

        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:digest, 9017))::text")
                .param("digest", digest).query(String.class).single();

        // Re-check after taking the per-subject lock to make duplicate DELETEs idempotent.
        existing = jdbc.sql("SELECT id, status FROM account_deletion_request WHERE subject_digest = :digest")
                .param("digest", digest)
                .query((rs, row) -> new ExistingRequest(rs.getObject("id", UUID.class), rs.getString("status")))
                .optional().orElse(null);
        if (existing != null) {
            return new AccountDeletionResponse(existing.id(), existing.status());
        }

        users.findByCognitoSub(cognitoSub).ifPresent(user -> {
            user.markDeletionPending(Instant.now(clock));
            users.saveAndFlush(user);
        });

        UUID requestId = jdbc.sql("""
                INSERT INTO account_deletion_request (subject_digest, cognito_sub, status)
                VALUES (:digest, :sub, 'PENDING')
                RETURNING id
                """).param("digest", digest).param("sub", cognitoSub)
                .query(UUID.class).single();

        audit(digest, "ACCOUNT_DELETION_REQUESTED", null);
        return new AccountDeletionResponse(requestId, "PENDING");
    }

    /** Processes a bounded batch; failures are intentionally isolated so one bad request does not starve others. */
    public int processPendingDeletions() {
        List<PendingRequest> pending = jdbc.sql("""
                SELECT id, subject_digest, cognito_sub
                FROM account_deletion_request
                WHERE status = 'PENDING'
                ORDER BY requested_at, id
                LIMIT 20
                """).query((rs, row) -> new PendingRequest(
                        rs.getObject("id", UUID.class), rs.getString("subject_digest"), rs.getString("cognito_sub")))
                .list();

        int completed = 0;
        for (PendingRequest request : pending) {
            try {
                processOne(request);
                completed++;
            } catch (RuntimeException failure) {
                // Never log the Cognito subject, profile data, or the raw exception, which can contain request data.
                LOG.warn("Account deletion cleanup failed; request remains queued for retry: {}", request.id());
            }
        }
        return completed;
    }

    private void processOne(PendingRequest request) {
        transaction.executeWithoutResult(status -> {
            PendingRequest locked = jdbc.sql("""
                    SELECT id, subject_digest, cognito_sub
                    FROM account_deletion_request
                    WHERE id = :id AND status = 'PENDING'
                    FOR UPDATE
                    """).param("id", request.id())
                    .query((rs, row) -> new PendingRequest(
                            rs.getObject("id", UUID.class), rs.getString("subject_digest"), rs.getString("cognito_sub")))
                    .optional().orElse(null);
            if (locked == null) return;

            cognito.deleteUser(locked.cognitoSub());

            UUID userId = users.findByCognitoSub(locked.cognitoSub()).map(AppUser::getId).orElse(null);
            if (userId != null) {
                jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:uid AS text), 0))::text")
                        .param("uid", userId.toString()).query(String.class).single();

                List<String> keys = jdbc.sql("SELECT storage_key FROM profile_photo WHERE user_id = :uid")
                        .param("uid", userId).query(String.class).list();
                // S3 DeleteObject is idempotent; a retry is safe if a later operation fails.
                for (String key : keys) photoStorage.delete(key);

                // Keep reported community content from becoming an identity leak; the post record remains for reports.
                jdbc.sql("""
                        UPDATE community_post
                        SET author_id = NULL, body = '[removed by account owner]', status = 'REMOVED',
                            updated_at = clock_timestamp()
                        WHERE author_id = :uid
                        """).param("uid", userId).update();

                // The user explicitly requested deletion: allow the consent FK cascade only within this transaction.
                jdbc.sql("SELECT set_config('pritimitra.allow_consent_delete', 'on', true)")
                        .query(String.class).single();
                jdbc.sql("DELETE FROM app_user WHERE id = :uid").param("uid", userId).update();
            }

            jdbc.sql("""
                    UPDATE account_deletion_request
                    SET status = 'COMPLETED', cognito_sub = NULL, completed_at = :completedAt
                    WHERE id = :id AND status = 'PENDING'
                    """).param("completedAt", OffsetDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC))
                    .param("id", locked.id()).update();
            audit(locked.subjectDigest(), "ACCOUNT_DELETION_COMPLETED", null);
        });
    }

    private void audit(String digest, String eventType, String consentType) {
        jdbc.sql("""
                INSERT INTO account_lifecycle_audit_event (subject_digest, event_type, consent_type)
                VALUES (:digest, :eventType, :consentType)
                """).param("digest", digest).param("eventType", eventType).param("consentType", consentType).update();
    }

    public void recordOptionalConsent(String cognitoSub, boolean accepted) {
        String digest = registry.digest(cognitoSub);
        transaction.executeWithoutResult(status -> {
            UUID userId = users.findByCognitoSub(cognitoSub)
                    .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
                    .map(AppUser::getId)
                    .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ELIGIBLE",
                            "Consent settings are available only to active accounts"));

            jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:uid AS text), 9018))::text")
                    .param("uid", userId.toString()).query(String.class).single();

            Boolean latest = jdbc.sql("""
                    SELECT accepted FROM consent_record
                    WHERE user_id = :uid AND consent_type = 'COMMUNITY_NICKNAME'
                    ORDER BY recorded_at DESC, id DESC LIMIT 1
                    """).param("uid", userId).query(Boolean.class).optional().orElse(false);
            if (latest != accepted) {
                jdbc.sql("""
                        INSERT INTO consent_record (user_id, consent_type, policy_version, accepted, recorded_at)
                        VALUES (:uid, 'COMMUNITY_NICKNAME', 'community-nick-v1', :accepted, :recordedAt)
                        """).param("uid", userId).param("accepted", accepted)
                        .param("recordedAt", OffsetDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC)).update();
                audit(digest, accepted ? "OPTIONAL_CONSENT_GRANTED" : "OPTIONAL_CONSENT_WITHDRAWN",
                        "COMMUNITY_NICKNAME");
            }
        });
    }

    public ConsentState getOptionalConsent(String cognitoSub) {
        UUID userId = users.findByCognitoSub(cognitoSub)
                .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
                .map(AppUser::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ELIGIBLE",
                        "Consent settings are available only to active accounts"));
        boolean accepted = jdbc.sql("""
                SELECT accepted FROM consent_record
                WHERE user_id = :uid AND consent_type = 'COMMUNITY_NICKNAME'
                ORDER BY recorded_at DESC, id DESC LIMIT 1
                """).param("uid", userId).query(Boolean.class).optional().orElse(false);
        return new ConsentState(accepted);
    }

    public record ConsentState(boolean communityNicknameAccepted) {}
    private record ExistingRequest(UUID id, String status) {}
    private record PendingRequest(UUID id, String subjectDigest, String cognitoSub) {}
}
