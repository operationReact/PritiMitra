import Link from "next/link";
import { BlindDateScheduler } from "./blind-date-scheduler";

export const metadata = { title: "Blind Date" };

export default function BlindDatePage() {
  return (
    <main className="app-page phase6-page">
      <div className="phase6-page-intro">
        <p className="phase6-eyebrow">Consent-first introductions</p>
        <h1>Blind Date</h1>
        <p>Opt into a time you choose. We pair only when another eligible member independently chooses the same slot.</p>
        <nav className="phase6-top-links" aria-label="PritiMitra features">
          <Link href="/community">Community</Link><Link href="/discover">Discover people</Link><Link href="/messages">Messages</Link>
        </nav>
      </div>
      <BlindDateScheduler />
    </main>
  );
}
