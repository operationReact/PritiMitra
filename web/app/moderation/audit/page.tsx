import Link from "next/link";
import { ModerationAuditHistory } from "../audit-history";

export const metadata = { title: "Audit history · PritiMitra" };

export default function ModerationAuditPage() {
  return (
    <main className="app-page moderation-page">
      <header className="moderation-header">
        <div className="moderation-heading-row">
          <div>
            <p className="moderation-eyebrow">Trust &amp; safety · Accountability</p>
            <h1>Audit history</h1>
            <p>Review recorded moderation actions with privacy-safe actor references and bounded exports.</p>
          </div>
          <Link className="moderation-audit-link" href="/moderation">Back to reports</Link>
        </div>
      </header>
      <ModerationAuditHistory />
    </main>
  );
}
