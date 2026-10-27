package com.pritimitra.backend.safety;

import com.pritimitra.backend.common.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Applies per-account rolling-hour limits to user-generated community posts and direct messages.
 *
 * The per-action lock row is held until the caller's surrounding transaction commits. This makes the
 * count-and-insert sequence safe under concurrent requests without taking a lock on the account row or
 * interfering with the conversation/match lock order used by messaging and blocking.
 */
@Service
public class UserActivityRateLimiter {
    static final int COMMUNITY_POSTS_PER_HOUR = 10;
    static final int MESSAGES_PER_HOUR = 60;

    private final JdbcClient jdbc;

    UserActivityRateLimiter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void checkCommunityPost(UUID userId) {
        lock(userId, "COMMUNITY_POST");
        long recent = jdbc.sql("""
                SELECT count(*) FROM community_post
                WHERE author_id = :user
                  AND created_at >= clock_timestamp() - interval '1 hour'
                """)
                .param("user", userId)
                .query(Long.class)
                .single();
        if (recent >= COMMUNITY_POSTS_PER_HOUR) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "COMMUNITY_POST_RATE_LIMITED",
                    "You have reached the community posting limit. Try again later.");
        }
    }

    public void checkMessage(UUID userId) {
        lock(userId, "MESSAGE_SEND");
        long recent = jdbc.sql("""
                SELECT count(*) FROM conversation_message
                WHERE sender_id = :user
                  AND created_at >= clock_timestamp() - interval '1 hour'
                """)
                .param("user", userId)
                .query(Long.class)
                .single();
        if (recent >= MESSAGES_PER_HOUR) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "MESSAGE_RATE_LIMITED",
                    "You have reached the messaging limit. Try again later.");
        }
    }

    private void lock(UUID userId, String action) {
        // This small per-user/action row serializes limit checks. It contains no message or post content.
        jdbc.sql("""
                INSERT INTO user_activity_rate_limit_lock (user_id, action)
                VALUES (:user, :action)
                ON CONFLICT (user_id, action)
                DO UPDATE SET touched_at = clock_timestamp()
                RETURNING user_id
                """)
                .param("user", userId)
                .param("action", action)
                .query(UUID.class)
                .single();
    }
}
