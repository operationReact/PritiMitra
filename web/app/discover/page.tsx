import { Suspense } from "react";
import { loadProtectedJson } from "@/lib/profile/server";
import { parseDiscoveryPage } from "@/lib/profile/models";
import { LikeButton } from "./like-button";

export const metadata = {
  title: "Discover",
};

export default function DiscoverPage() {
  return (
    <main className="app-page">
      <h1>Discover</h1>
      <Suspense fallback={<p>Finding profiles…</p>}>
        <DiscoveryContent />
      </Suspense>
    </main>
  );
}

async function DiscoveryContent() {
  const load = await loadProtectedJson(["v1", "discovery"]);
  if (load.kind === "unavailable") {
    return <p role="alert">Discovery is temporarily unavailable. Please try again.</p>;
  }
  if (load.kind === "signed-out") {
    return <p>Please sign in to browse profiles.</p>;
  }
  if (load.kind === "not-eligible") {
    return <p>Discovery is available once your account is verified and your profile is complete.</p>;
  }

  const page = parseDiscoveryPage(load.body);
  if (!page) {
    return <p role="alert">Discovery is temporarily unavailable. Please try again.</p>;
  }
  if (page.items.length === 0) {
    return <p>No profiles to show right now. Check back later.</p>;
  }

  return (
    <>
      <ul className="discovery-grid">
        {page.items.map((card) => (
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
      {page.nextCursor ? <p className="muted">Showing the first page of profiles.</p> : null}
    </>
  );
}
