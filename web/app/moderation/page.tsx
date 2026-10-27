import { ModerationQueue } from "./moderation-queue";

export const metadata = { title: "Moderation queue · PritiMitra" };

export default function ModerationPage() {
  return (
    <main className="app-page moderation-page">
      <header className="moderation-header">
        <p className="moderation-eyebrow">Trust &amp; safety</p>
        <h1>Moderation queue</h1>
        <p>Review private reports, make a recorded decision, and keep the community safer.</p>
      </header>
      <ModerationQueue />
    </main>
  );
}
