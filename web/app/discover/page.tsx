import { Suspense } from "react";
import { loadProtectedJson } from "@/lib/profile/server";
import { parseDiscoveryPage } from "@/lib/profile/models";
import { DiscoveryFeed } from "./discovery-feed";
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

  return <DiscoveryFeed initialItems={page.items} initialCursor={page.nextCursor} />;
}
