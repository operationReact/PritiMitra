import Link from "next/link";
import { ModerationQueue } from "./moderation-queue";

export const metadata = { title: "Moderation queue · PritiMitra" };

export default function ModerationPage() {
  return (
    <main className="app-page moderation-page">
      <header className="moderation-header">
        <div className="moderation-heading-row">
          <div>
            <p className="moderation-eyebrow">Trust &amp; safety</p>
            <h1>Moderation queue</h1>
            <p>Review private reports, make a recorded decision, and keep the community safer.</p>
          </div>
          <Link className="moderation-audit-link" href="/moderation/audit">Audit history <span aria-hidden="true">↗</span></Link>
        </div>
      </header>
      <ModerationQueue />
    </main>
  );
}
