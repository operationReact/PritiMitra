package com.pritimitra.backend.photos;

import com.pritimitra.backend.common.ApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Owner-only profile photo management. The storage key is generated here and never accepted from clients. */
@Service
class PhotoService {

	static final int MAX_PHOTOS = 6;
	static final long MAX_BYTES = 5L * 1024 * 1024;

	private static final Logger LOG = LoggerFactory.getLogger(PhotoService.class);
	private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
	private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

	private static final String PHOTO_COLUMNS = "id, content_type, size_bytes, review_status, created_at";

	private final JdbcClient jdbc;
	private final PhotoStorage storage;

	PhotoService(JdbcClient jdbc, PhotoStorage storage) {
		this.jdbc = jdbc;
		this.storage = storage;
	}

	@Transactional
	PhotoResponse upload(String cognitoSub, MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			throw invalidPhoto("A photo file is required");
		}
		if (file.getSize() > MAX_BYTES) {
			throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PHOTO_TOO_LARGE", "Photos must be 5 MB or smaller");
		}
		byte[] bytes = file.getBytes();
		String contentType = detectContentType(bytes);
		if (contentType == null) {
			throw invalidPhoto("Only JPEG, PNG and WebP images are accepted");
		}

		UUID userId = activeUserId(cognitoSub);
		// Serialises concurrent uploads for one user so the photo limit cannot be exceeded by a race.
		jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(CAST(:uid AS text), 0))::text")
				.param("uid", userId.toString())
				.query(String.class)
				.single();
		long existing = jdbc.sql("SELECT count(*) FROM profile_photo WHERE user_id = :uid")
				.param("uid", userId)
				.query(Long.class)
				.single();
		if (existing >= MAX_PHOTOS) {
			throw new ApiException(HttpStatus.CONFLICT, "PHOTO_LIMIT_REACHED",
					"A profile can have at most " + MAX_PHOTOS + " photos");
		}

		String key = "profiles/" + userId + "/" + UUID.randomUUID();
		storage.put(key, bytes, contentType);
		try {
			return jdbc.sql("""
					INSERT INTO profile_photo (user_id, storage_key, content_type, size_bytes)
					VALUES (:uid, :key, :contentType, :size)
					RETURNING id, content_type, size_bytes, review_status, created_at
					""")
					.param("uid", userId)
					.param("key", key)
					.param("contentType", contentType)
					.param("size", bytes.length)
					.query(PhotoService::toResponse)
					.single();
		} catch (RuntimeException ex) {
			deleteObjectQuietly(key);
			throw ex;
		}
	}

	List<PhotoResponse> list(String cognitoSub) {
		UUID userId = activeUserId(cognitoSub);
		return jdbc.sql("SELECT " + PHOTO_COLUMNS + " FROM profile_photo WHERE user_id = :uid ORDER BY created_at, id")
				.param("uid", userId)
				.query(PhotoService::toResponse)
				.list();
	}

	/** Returns the bytes of a photo owned by the caller. Other owners' photos look identical to missing ones. */
	PhotoContent content(String cognitoSub, UUID photoId) {
		UUID userId = activeUserId(cognitoSub);
		Optional<PhotoContent> row = jdbc.sql("""
				SELECT storage_key, content_type FROM profile_photo WHERE id = :id AND user_id = :uid
				""")
				.param("id", photoId)
				.param("uid", userId)
				.query((rs, rowNum) -> new PhotoRecord(rs.getString("storage_key"), rs.getString("content_type")))
				.optional()
				.map(record -> new PhotoContent(storage.get(record.storageKey()), record.contentType()));
		return row.orElseThrow(PhotoService::photoNotFound);
	}

	@Transactional
	void delete(String cognitoSub, UUID photoId) {
		UUID userId = activeUserId(cognitoSub);
		String key = jdbc.sql("DELETE FROM profile_photo WHERE id = :id AND user_id = :uid RETURNING storage_key")
				.param("id", photoId)
				.param("uid", userId)
				.query(String.class)
				.optional()
				.orElseThrow(PhotoService::photoNotFound);
		deleteObjectQuietly(key);
	}

	/** Detects the format from file signatures, ignoring the client-supplied Content-Type header. */
	static String detectContentType(byte[] bytes) {
		if (startsWith(bytes, 0, JPEG)) {
			return "image/jpeg";
		}
		if (startsWith(bytes, 0, PNG)) {
			return "image/png";
		}
		if (bytes.length >= 12 && "RIFF".equals(ascii(bytes, 0, 4)) && "WEBP".equals(ascii(bytes, 8, 4))) {
			return "image/webp";
		}
		return null;
	}

	private UUID activeUserId(String cognitoSub) {
		return jdbc.sql("SELECT id FROM app_user WHERE cognito_sub = :sub AND status = 'ACTIVE'")
				.param("sub", cognitoSub)
				.query(UUID.class)
				.optional()
				.orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ELIGIBLE",
						"Photos are available only to active accounts"));
	}

	private void deleteObjectQuietly(String key) {
		try {
			storage.delete(key);
		} catch (RuntimeException ex) {
			// The key is deliberately not logged, so storage identifiers stay out of logs.
			LOG.warn("Profile photo object could not be removed from storage; it needs manual cleanup");
		}
	}

	private static PhotoResponse toResponse(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new PhotoResponse(
				rs.getObject("id", UUID.class),
				rs.getString("content_type"),
				rs.getInt("size_bytes"),
				rs.getString("review_status"),
				rs.getObject("created_at", OffsetDateTime.class));
	}

	private static ApiException invalidPhoto(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHOTO", message);
	}

	private static ApiException photoNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "PHOTO_NOT_FOUND", "Photo not found");
	}

	private static boolean startsWith(byte[] bytes, int offset, byte[] signature) {
		if (bytes.length < offset + signature.length) {
			return false;
		}
		for (int i = 0; i < signature.length; i++) {
			if (bytes[offset + i] != signature[i]) {
				return false;
			}
		}
		return true;
	}

	private static String ascii(byte[] bytes, int offset, int length) {
		return new String(bytes, offset, length, StandardCharsets.US_ASCII);
	}

	record PhotoContent(byte[] bytes, String contentType) {
	}

	private record PhotoRecord(String storageKey, String contentType) {
	}
}
