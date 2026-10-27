"use client";

import { useState } from "react";
import { parseDiscoveryPage, type DiscoveryCard } from "@/lib/profile/models";
import { LikeButton } from "./like-button";

const PAGE_SIZE = 20;

export function DiscoveryFeed({
  initialItems,
  initialCursor,
}: {
  initialItems: DiscoveryCard[];
  initialCursor: string | null;
}) {
  const [items, setItems] = useState(initialItems);
  const [cursor, setCursor] = useState(initialCursor);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function loadMore() {
    if (!cursor || loading) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const params = new URLSearchParams({ size: String(PAGE_SIZE), cursor });
      const response = await fetch(`/api/backend/v1/discovery?${params.toString()}`, {
        credentials: "same-origin",
        headers: { Accept: "application/json" },
      });
      const page = response.ok ? parseDiscoveryPage(await response.json()) : null;
      if (!page) {
        setError(
          response.status === 401
            ? "Your session has expired. Please sign in again."
            : "We could not load more profiles. Please try again.",
        );
        return;
      }
      setItems((current) => {
        const seen = new Set(current.map((card) => card.publicId));
        return [...current, ...page.items.filter((card) => !seen.has(card.publicId))];
      });
      setCursor(page.nextCursor);
    } catch {
      setError("We could not reach PritiMitra. Check your connection and try again.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <>
      <ul className="discovery-grid">
        {items.map((card) => (
          <li key={card.publicId} className="profile-card">
            <h2>{card.displayName}</h2>
            {card.bio ? <p>{card.bio}</p> : null}
            {card.profilePrompt ? <p className="profile-prompt">{card.profilePrompt}</p> : null}
            {card.goals.length > 0 ? <p>Goals: {card.goals.join(", ")}</p> : null}
            {card.interests.length > 0 ? <p>Interests: {card.interests.join(", ")}</p> : null}
            <LikeButton publicId={card.publicId} displayName={card.displayName} />
          </li>
        ))}
      </ul>
      {error ? <p role="alert">{error}</p> : null}
      {cursor ? (
        <div className="load-more">
          <button type="button" className="button" onClick={loadMore} disabled={loading}>
            {loading ? "Loading profiles…" : error ? "Try again" : "Load more profiles"}
          </button>
        </div>
      ) : null}
    </>
  );
}
