package com.pritimitra.backend.photos;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Owner-facing photo metadata. The storage key, bucket and object bytes are never included. */
record PhotoResponse(UUID id, String contentType, int sizeBytes, String reviewStatus, OffsetDateTime createdAt) {
}
