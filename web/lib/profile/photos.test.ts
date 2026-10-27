import { describe, expect, it } from "vitest";
import { parseOwnPhoto, parseOwnPhotoList, photoContentUrl, precheckPhotoFile, uploadFailureFor } from "./photos";

const photo = {
  id: "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
  contentType: "image/jpeg",
  sizeBytes: 1024,
  reviewStatus: "PENDING",
  createdAt: "2026-01-01T00:00:00Z",
};

describe("parseOwnPhoto", () => {
  it("accepts a complete photo and drops unknown fields", () => {
    expect(parseOwnPhoto({ ...photo, storageKey: "secret/key" })).toEqual(photo);
  });

  it("rejects a non-UUID id, missing fields, and non-objects", () => {
    expect(parseOwnPhoto({ ...photo, id: "../etc/passwd" })).toBeNull();
    expect(parseOwnPhoto({ ...photo, sizeBytes: "1024" })).toBeNull();
    const withoutDate: Record<string, unknown> = { ...photo };
    delete withoutDate.createdAt;
    expect(parseOwnPhoto(withoutDate)).toBeNull();
    expect(parseOwnPhoto(null)).toBeNull();
    expect(parseOwnPhoto("photo")).toBeNull();
  });
});

describe("parseOwnPhotoList", () => {
  it("returns an empty list for an empty array", () => {
    expect(parseOwnPhotoList([])).toEqual([]);
  });

  it("rejects the whole list when any entry is invalid", () => {
    expect(parseOwnPhotoList([photo, { id: "bad" }])).toBeNull();
    expect(parseOwnPhotoList({ photos: [photo] })).toBeNull();
  });
});

describe("photoContentUrl", () => {
  it("builds a same-origin BFF path for a valid id", () => {
    expect(photoContentUrl(photo.id)).toBe(`/api/backend/v1/profile/photos/${photo.id}/content`);
  });
});

describe("precheckPhotoFile", () => {
  it("accepts JPEG and PNG within the size limit", () => {
    expect(precheckPhotoFile({ type: "image/jpeg", size: 1000 })).toBeNull();
    expect(precheckPhotoFile({ type: "image/png", size: 5 * 1024 * 1024 })).toBeNull();
  });

  it("rejects other types including WebP and SVG", () => {
    expect(precheckPhotoFile({ type: "image/webp", size: 1000 })).not.toBeNull();
    expect(precheckPhotoFile({ type: "image/svg+xml", size: 1000 })).not.toBeNull();
    expect(precheckPhotoFile({ type: "", size: 1000 })).not.toBeNull();
  });

  it("rejects oversized and empty files", () => {
    expect(precheckPhotoFile({ type: "image/jpeg", size: 5 * 1024 * 1024 + 1 })).not.toBeNull();
    expect(precheckPhotoFile({ type: "image/jpeg", size: 0 })).not.toBeNull();
  });
});

describe("uploadFailureFor", () => {
  it("does not offer retry for validation, limit, or session failures", () => {
    expect(uploadFailureFor(400, "INVALID_PHOTO").retryable).toBe(false);
    expect(uploadFailureFor(400, "PHOTO_DIMENSIONS_TOO_LARGE").retryable).toBe(false);
    expect(uploadFailureFor(413, null).retryable).toBe(false);
    expect(uploadFailureFor(409, "PHOTO_LIMIT_REACHED").retryable).toBe(false);
    expect(uploadFailureFor(401, null).retryable).toBe(false);
  });

  it("offers retry for network, timeout, and storage failures", () => {
    expect(uploadFailureFor(0, null).retryable).toBe(true);
    expect(uploadFailureFor(504, "backend_timeout").retryable).toBe(true);
    expect(uploadFailureFor(503, "PHOTO_STORAGE_UNAVAILABLE").retryable).toBe(true);
    expect(uploadFailureFor(502, "backend_unavailable").retryable).toBe(true);
  });

  it("never echoes server detail in the message", () => {
    const message = uploadFailureFor(500, "PHOTO_STORAGE_UNAVAILABLE").message;
    expect(message).not.toMatch(/key|s3|bucket|trace|exception|\//i);
  });
});
