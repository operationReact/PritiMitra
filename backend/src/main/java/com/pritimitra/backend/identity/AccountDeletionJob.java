package com.pritimitra.backend.identity;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retries incomplete deletion requests without relying on the deleted user's expired session. */
@Component
public class AccountDeletionJob {

    private final AccountDeletionService deletions;

    public AccountDeletionJob(AccountDeletionService deletions) {
        this.deletions = deletions;
    }

    @Scheduled(fixedDelayString = "${pritimitra.account-deletion.poll-interval-ms:30000}")
    void processPendingDeletions() {
        deletions.processPendingDeletions();
    }
}
