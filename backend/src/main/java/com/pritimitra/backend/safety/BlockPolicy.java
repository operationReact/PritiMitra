package com.pritimitra.backend.safety;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Server-side block checks. A block in either direction prevents likes, matches and messages. */
public final class BlockPolicy {

	private BlockPolicy() {
	}

	public static boolean blockedEitherWay(JdbcClient jdbc, UUID a, UUID b) {
		return Boolean.TRUE.equals(jdbc.sql("""
				SELECT EXISTS (
				  SELECT 1 FROM user_block
				  WHERE (blocker_id = :a AND blocked_id = :b) OR (blocker_id = :b AND blocked_id = :a)
				)
				""")
				.param("a", a)
				.param("b", b)
				.query(Boolean.class)
				.single());
	}
}
