export const MAX_PROFILE_PHOTOS = 6;
export const MAX_PHOTO_BYTES = 5 * 1024 * 1024;
export const ACCEPTED_PHOTO_TYPES = ["image/jpeg", "image/png"] as const;

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export type OwnPhoto = {
  id: string;
  contentType: string;
  sizeBytes: number;
  reviewStatus: string;
  createdAt: string;
};

export function parseOwnPhoto(value: unknown): OwnPhoto | null {
  if (typeof value !== "object" || value === null) {
    return null;
  }
  const record = value as Record<string, unknown>;
  if (
    typeof record.id !== "string" ||
    !UUID_PATTERN.test(record.id) ||
    typeof record.contentType !== "string" ||
    typeof record.sizeBytes !== "number" ||
    typeof record.reviewStatus !== "string" ||
    typeof record.createdAt !== "string"
  ) {
    return null;
  }
  return {
    id: record.id,
    contentType: record.contentType,
    sizeBytes: record.sizeBytes,
    reviewStatus: record.reviewStatus,
    createdAt: record.createdAt,
  };
}

/** Returns the parsed list, or null when the payload is not an array of valid photos. */
export function parseOwnPhotoList(value: unknown): OwnPhoto[] | null {
  if (!Array.isArray(value)) {
    return null;
  }
  const photos: OwnPhoto[] = [];
  for (const item of value) {
    const photo = parseOwnPhoto(item);
    if (!photo) {
      return null;
    }
    photos.push(photo);
  }
  return photos;
}

export function photoContentUrl(id: string): string {
  return `/api/backend/v1/profile/photos/${id}/content`;
}

/** Client-side precheck only. The server re-validates type, size, and image content. */
export function precheckPhotoFile(file: { type: string; size: number }): string | null {
  if (!(ACCEPTED_PHOTO_TYPES as readonly string[]).includes(file.type)) {
    return "Choose a JPEG or PNG image.";
  }
  if (file.size > MAX_PHOTO_BYTES) {
    return "Choose an image up to 5 MB.";
  }
  if (file.size === 0) {
    return "The selected file is empty.";
  }
  return null;
}

/** Maps server outcomes to user messages. Only transient failures offer a retry. */
export function uploadFailureFor(status: number, code: string | null): { message: string; retryable: boolean } {
  if (status === 401) {
    return { message: "Your session has expired. Please sign in again.", retryable: false };
  }
  if (code === "PHOTO_LIMIT_REACHED" || status === 409) {
    return { message: `You can keep up to ${MAX_PROFILE_PHOTOS} photos. Remove one to add another.`, retryable: false };
  }
  if (status === 413 || code === "PHOTO_TOO_LARGE" || code === "payload_too_large") {
    return { message: "That image is larger than 5 MB. Choose a smaller image.", retryable: false };
  }
  if (code === "PHOTO_DIMENSIONS_TOO_LARGE") {
    return { message: "That image is too large in pixels. Choose a smaller image.", retryable: false };
  }
  if (code === "INVALID_PHOTO" || code === "invalid_request" || status === 400 || status === 415) {
    return { message: "We could not read that image. Choose a different JPEG or PNG.", retryable: false };
  }
  if (status === 0) {
    return { message: "Upload did not finish. Check your connection and try again.", retryable: true };
  }
  if (status === 504) {
    return { message: "PritiMitra took too long to respond. Try again.", retryable: true };
  }
  if (status >= 500) {
    return { message: "Photo storage is unavailable right now. Try again shortly.", retryable: true };
  }
  return { message: "We could not add that photo. Please try again.", retryable: false };
}
