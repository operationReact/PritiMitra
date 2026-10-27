"use client";

import { useState } from "react";
import { parseOwnProfile } from "@/lib/profile/models";

export function DiscoverabilityToggle({ initialDiscoverable }: { initialDiscoverable: boolean }) {
  const [discoverable, setDiscoverable] = useState(initialDiscoverable);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function toggle() {
    setPending(true);
    setError(null);
    try {
      const response = await fetch("/api/backend/v1/profile/discoverability", {
        method: "PUT",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ discoverable: !discoverable }),
      });
      const parsed = response.ok ? parseOwnProfile(await response.json()) : null;
      if (!parsed) {
        setError(
          response.status === 401
            ? "Your session has expired. Please sign in again."
            : "We could not update your visibility. Please try again.",
        );
        return;
      }
      setDiscoverable(parsed.discoverable);
    } catch {
      setError("We could not reach PritiMitra. Check your connection and try again.");
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="profile-toggle">
      <p>
        {discoverable
          ? "Your profile is visible in discovery."
          : "Your profile is private and hidden from discovery."}
      </p>
      <button type="button" className="button" onClick={toggle} disabled={pending} aria-busy={pending}>
        {pending ? "Saving…" : discoverable ? "Hide from discovery" : "Show in discovery"}
      </button>
      {error ? <p role="alert">{error}</p> : null}
    </div>
  );
}
