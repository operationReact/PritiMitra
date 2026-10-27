import Link from "next/link";
import { CommunityBrowser } from "./community-browser";

export const metadata = { title: "Community" };

export default function CommunityPage() {
  return (
    <main className="app-page phase6-page">
      <div className="phase6-page-intro">
        <p className="phase6-eyebrow">Connection beyond dating</p>
        <h1>Community</h1>
        <p>Choose the spaces that feel right for you. Joining is optional, and only members can read or post in a group.</p>
        <nav className="phase6-top-links" aria-label="PritiMitra features">
          <Link href="/blind-date">Blind Date scheduling</Link>
          <Link href="/discover">Discover people</Link>
          <Link href="/messages">Messages</Link>
        </nav>
      </div>
      <CommunityBrowser />
    </main>
  );
}
