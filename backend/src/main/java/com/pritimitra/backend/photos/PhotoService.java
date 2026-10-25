package com.pritimitra.backend.photos;

import com.pritimitra.backend.common.ApiException;
import java.io.IOException;
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

	private static final String PHOTO_COLUMNS = "id, content_type, size_bytes, review_status, created_at";

	private final JdbcClient jdbc;
	private final PhotoStorage storage;
	private final PhotoImageProcessor imageProcessor;

	PhotoService(JdbcClient jdbc, PhotoStorage storage, PhotoImageProcessor imageProcessor) {
		this.jdbc = jdbc;
		this.storage = storage;
		this.imageProcessor = imageProcessor;
	}

	@Transactional
	PhotoResponse upload(String cognitoSub, MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			throw invalidPhoto("A photo file is required");
		}
		if (file.getSize() > MAX_BYTES) {
			throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PHOTO_TOO_LARGE", "Photos must be 5 MB or smaller");
		}
		// The filename, extension and client Content-Type are ignored; only decoded pixels decide acceptance.
		PhotoImageProcessor.ProcessedPhoto photo = imageProcessor.process(file.getBytes());
		byte[] bytes = photo.bytes();
		String contentType = photo.contentType();
		if (bytes.length > MAX_BYTES) {
			throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PHOTO_TOO_LARGE", "Photos must be 5 MB or smaller");
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

	record PhotoContent(byte[] bytes, String contentType) {
	}

	private record PhotoRecord(String storageKey, String contentType) {
	}
}
