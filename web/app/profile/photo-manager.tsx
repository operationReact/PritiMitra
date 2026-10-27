"use client";

import "./photo-manager.css";
import { useRef, useState } from "react";
import {
  ACCEPTED_PHOTO_TYPES,
  MAX_PROFILE_PHOTOS,
  parseOwnPhoto,
  parseOwnPhotoList,
  photoContentUrl,
  precheckPhotoFile,
  uploadFailureFor,
  type OwnPhoto,
} from "@/lib/profile/photos";

const PHOTOS_PATH = "/api/backend/v1/profile/photos";

type UploadProgress = { percent: number; processing: boolean };
type UploadFailure = { message: string; retryable: boolean; file: File | null };
type UploadResult =
  | { ok: true; photo: OwnPhoto }
  | { ok: false; status: number; code: string | null };

export function PhotoManager({ initialPhotos }: { initialPhotos: OwnPhoto[] | null }) {
  const [photos, setPhotos] = useState<OwnPhoto[]>(initialPhotos ?? []);
  const [listFailed, setListFailed] = useState(initialPhotos === null);
  const [progress, setProgress] = useState<UploadProgress | null>(null);
  const [failure, setFailure] = useState<UploadFailure | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [confirmingId, setConfirmingId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [brokenIds, setBrokenIds] = useState<ReadonlySet<string>>(new Set());
  const uploadInFlight = useRef(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const atLimit = photos.length >= MAX_PROFILE_PHOTOS;
  const busy = progress !== null || deletingId !== null;

  async function refresh(): Promise<void> {
    try {
      const response = await fetch(PHOTOS_PATH, { credentials: "same-origin" });
      const parsed = response.ok ? parseOwnPhotoList(await response.json()) : null;
      if (!parsed) {
        setListFailed(true);
        return;
      }
      setPhotos(parsed);
      setListFailed(false);
      setBrokenIds(new Set());
    } catch {
      setListFailed(true);
    }
  }

  async function startUpload(file: File): Promise<void> {
    if (uploadInFlight.current) {
      return;
    }
    uploadInFlight.current = true;
    setFailure(null);
    setStatus(null);
    setProgress({ percent: 0, processing: false });
    const result = await sendPhoto(file, (percent) =>
      setProgress({ percent, processing: percent >= 100 }),
    );
    uploadInFlight.current = false;
    setProgress(null);
    if (inputRef.current) {
      inputRef.current.value = "";
    }

    if (result.ok) {
      setStatus("Photo added. It is visible only to you.");
      await refresh();
      return;
    }
    const mapped = uploadFailureFor(result.status, result.code);
    setFailure({ ...mapped, file: mapped.retryable ? file : null });
  }

  function onFileChosen(file: File | undefined) {
    if (!file) {
      return;
    }
    const problem = atLimit
      ? `You can keep up to ${MAX_PROFILE_PHOTOS} photos. Remove one to add another.`
      : precheckPhotoFile(file);
    if (problem) {
      setStatus(null);
      setFailure({ message: problem, retryable: false, file: null });
      if (inputRef.current) {
        inputRef.current.value = "";
      }
      return;
    }
    void startUpload(file);
  }

  async function deletePhoto(id: string): Promise<void> {
    setDeletingId(id);
    setDeleteError(null);
    setStatus(null);
    try {
      const response = await fetch(`${PHOTOS_PATH}/${id}`, {
        method: "DELETE",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
      });
      if (response.status === 204) {
        setConfirmingId(null);
        setStatus("Photo removed.");
        await refresh();
        return;
      }
      setDeleteError(
        response.status === 401
          ? "Your session has expired. Please sign in again."
          : response.status === 404
            ? "That photo was already removed."
            : "We could not remove that photo. Please try again.",
      );
      if (response.status === 404) {
        await refresh();
      }
    } catch {
      setDeleteError("We could not reach PritiMitra. Check your connection and try again.");
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <section className="photo-manager" aria-labelledby="photo-manager-title">
      <h2 id="photo-manager-title">Your photos</h2>
      <p className="photo-manager-note">
        Photos are visible only to you for now. They are not identity-verified and are not yet reviewed by moderation.
      </p>
      <p className="photo-manager-count" aria-live="polite">
        {photos.length} of {MAX_PROFILE_PHOTOS} photos
      </p>

      {listFailed ? (
        <div className="photo-manager-error">
          <p role="alert">We could not load your photos.</p>
          <button type="button" className="button" onClick={() => void refresh()} disabled={busy}>
            Try again
          </button>
        </div>
      ) : null}

      {!listFailed && photos.length === 0 ? (
        <p className="photo-manager-empty">No photos yet. Add a JPEG or PNG up to 5 MB.</p>
      ) : null}

      {photos.length > 0 ? (
        <ul className="photo-grid">
          {photos.map((photo, index) => (
            <li key={photo.id} className="photo-tile">
              {brokenIds.has(photo.id) ? (
                <p className="photo-tile-missing">This photo could not be displayed.</p>
              ) : (
                // Plain img: the source is an authenticated same-origin BFF route, which next/image's optimizer cannot fetch with the session cookie.
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={photoContentUrl(photo.id)}
                  alt={`Your profile photo ${index + 1}`}
                  width={160}
                  height={160}
                  loading="lazy"
                  onError={() => setBrokenIds((ids) => new Set(ids).add(photo.id))}
                />
              )}
              {confirmingId === photo.id ? (
                <div className="photo-confirm">
                  <p>Remove this photo?</p>
                  <button
                    type="button"
                    className="button"
                    onClick={() => void deletePhoto(photo.id)}
                    disabled={deletingId !== null}
                    aria-busy={deletingId === photo.id}
                  >
                    {deletingId === photo.id ? "Removing…" : "Remove"}
                  </button>
                  <button
                    type="button"
                    className="button"
                    onClick={() => setConfirmingId(null)}
                    disabled={deletingId !== null}
                  >
                    Cancel
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  className="button"
                  onClick={() => {
                    setDeleteError(null);
                    setConfirmingId(photo.id);
                  }}
                  disabled={busy}
                >
                  Remove photo {index + 1}
                </button>
              )}
            </li>
          ))}
        </ul>
      ) : null}
      {deleteError ? <p role="alert">{deleteError}</p> : null}

      <div className="photo-upload">
        <label htmlFor="photo-file">Add a photo</label>
        <input
          ref={inputRef}
          id="photo-file"
          type="file"
          accept={ACCEPTED_PHOTO_TYPES.join(",")}
          disabled={busy || atLimit}
          onChange={(event) => onFileChosen(event.target.files?.[0])}
        />
        {progress ? (
          <div className="photo-progress" role="status">
            <progress value={progress.percent} max={100} aria-label="Upload progress" />
            <span>
              {progress.processing ? "Checking your photo…" : `Uploading… ${progress.percent}%`}
            </span>
          </div>
        ) : null}
        {status ? <p role="status">{status}</p> : null}
        {failure ? (
          <div className="photo-upload-error">
            <p role="alert">{failure.message}</p>
            {failure.retryable && failure.file ? (
              <button
                type="button"
                className="button"
                onClick={() => failure.file && void startUpload(failure.file)}
                disabled={busy}
              >
                Try upload again
              </button>
            ) : null}
          </div>
        ) : null}
      </div>
    </section>
  );
}

/** Sends the file as multipart with XMLHttpRequest so upload progress is observable. */
function sendPhoto(file: File, onProgress: (percent: number) => void): Promise<UploadResult> {
  return new Promise((resolve) => {
    const request = new XMLHttpRequest();
    const body = new FormData();
    body.append("file", file);
    request.open("POST", PHOTOS_PATH);
    request.responseType = "text";
    request.upload.onprogress = (event) => {
      if (event.lengthComputable && event.total > 0) {
        onProgress(Math.min(100, Math.round((event.loaded / event.total) * 100)));
      }
    };
    request.onload = () => {
      if (request.status === 201) {
        const photo = parseOwnPhoto(safeJson(request.responseText));
        resolve(photo ? { ok: true, photo } : { ok: false, status: 0, code: null });
        return;
      }
      const errorBody = safeJson(request.responseText);
      const code = readCode(errorBody);
      resolve({ ok: false, status: request.status, code });
    };
    request.onerror = () => resolve({ ok: false, status: 0, code: null });
    request.ontimeout = () => resolve({ ok: false, status: 0, code: null });
    request.send(body);
  });
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function readCode(body: unknown): string | null {
  if (typeof body !== "object" || body === null) {
    return null;
  }
  const record = body as Record<string, unknown>;
  if (typeof record.code === "string") {
    return record.code;
  }
  if (typeof record.error === "string") {
    return record.error;
  }
  return null;
}
