"use client";

import { useState } from "react";
import { parseDiscoveryPage, type DiscoveryCard } from "@/lib/profile/models";
import { LikeButton } from "./like-button";

const PAGE_SIZE = 20;

function initialsFor(name: string): string {
  return name.trim().split(/\s+/).slice(0, 2).map((part) => part.charAt(0)).join("").toUpperCase() || "♡";
}

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
    if (!cursor || loading) return;
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
        setError(response.status === 401
          ? "Your session has expired. Please sign in again."
          : "We could not load more profiles. Please try again.");
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
      <div className="discovery-grid" role="list" aria-label="People to discover">
        {items.map((card, index) => (
          <article key={card.publicId} className={`profile-card discovery-profile-card discovery-tone-${index % 4}`} role="listitem">
            <div className="discovery-profile-cover" aria-hidden="true">
              <span className="discovery-cover-caption">A little introduction</span>
              <span className="discovery-cover-orbit discovery-cover-orbit-one" />
              <span className="discovery-cover-orbit discovery-cover-orbit-two" />
              <span className="discovery-initial-avatar">{initialsFor(card.displayName)}</span>
              <span className="discovery-cover-spark">✦</span>
            </div>
            <div className="discovery-profile-content">
              <div className="discovery-card-overline">
                <span>OPEN TO CONNECTION</span><span className="discovery-heart-mark" aria-hidden="true">♡</span>
              </div>
              <h2>{card.displayName}</h2>
              {card.bio ? <p className="discovery-bio">{card.bio}</p> : <p className="discovery-bio discovery-bio-muted">A new face, a fresh story. Start with a hello.</p>}
              {card.profilePrompt ? <blockquote className="discovery-prompt">“{card.profilePrompt}”</blockquote> : null}
              {card.goals.length > 0 ? (
                <div className="discovery-chip-group"><span className="discovery-chip-label">Looking for</span><div className="discovery-chips">
                  {card.goals.map((goal) => <span className="discovery-chip discovery-chip-goal" key={goal}>{goal.replaceAll("_", " ").toLowerCase()}</span>)}
                </div></div>
              ) : null}
              {card.interests.length > 0 ? (
                <div className="discovery-chip-group"><span className="discovery-chip-label">Into</span><div className="discovery-chips">
                  {card.interests.slice(0, 6).map((interest) => <span className="discovery-chip" key={interest}>{interest}</span>)}
                </div></div>
              ) : null}
              <div className="discovery-card-actions">
                <LikeButton publicId={card.publicId} displayName={card.displayName} />
                <span className="discovery-consent-note"><span aria-hidden="true">✦</span> Kindness looks good on everyone</span>
              </div>
            </div>
          </article>
        ))}
      </div>
      {error ? <p role="alert" className="discovery-feed-error">{error}</p> : null}
      {cursor ? (
        <div className="load-more">
          <button type="button" className="button" onClick={loadMore} disabled={loading}>
            {loading ? "Finding more people…" : error ? "Try again" : "Meet more people"} <span aria-hidden="true">↗</span>
          </button>
        </div>
      ) : null}
    </>
  );
}
