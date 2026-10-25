"use client";

import { useState } from "react";
import { parseLikeStatus } from "@/lib/profile/models";

/**
 * Likes are idempotent on the server. The discovery response carries no viewer-like flag, so the button
 * starts in the "Like" state; re-liking an already-liked profile is harmless.
 */
export function LikeButton({ publicId, displayName }: { publicId: string; displayName: string }) {
  const [liked, setLiked] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function toggle() {
    setPending(true);
    setError(null);
    try {
      const response = await fetch(`/api/backend/v1/likes/${encodeURIComponent(publicId)}`, {
        method: liked ? "DELETE" : "PUT",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
      });
      const status = response.ok ? parseLikeStatus(await response.json()) : null;
      if (status === null) {
        setError(
          response.status === 401
            ? "Your session has expired. Please sign in again."
            : "We could not update your like. Please try again.",
        );
        return;
      }
      setLiked(status);
    } catch {
      setError("We could not reach PritiMitra. Check your connection and try again.");
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="like-control">
      <button
        type="button"
        className="button"
        onClick={toggle}
        disabled={pending}
        aria-pressed={liked}
        aria-label={`${liked ? "Unlike" : "Like"} ${displayName}`}
      >
        {pending ? "Saving…" : liked ? "Liked" : "Like"}
      </button>
      {error ? <p role="alert">{error}</p> : null}
    </div>
  );
}
