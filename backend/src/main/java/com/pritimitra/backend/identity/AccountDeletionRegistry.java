package com.pritimitra.backend.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Looks up irreversible subject digests so a deleted account cannot be provisioned from a still-valid JWT. */
@Component
public class AccountDeletionRegistry {

    private final JdbcClient jdbc;

    public AccountDeletionRegistry(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public String digest(String cognitoSub) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(cognitoSub.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the runtime", impossible);
        }
    }

    public boolean isDeletionRequestedOrCompleted(String cognitoSub) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM account_deletion_request WHERE subject_digest = :digest)")
                .param("digest", digest(cognitoSub))
                .query(Boolean.class)
                .single();
    }
}
