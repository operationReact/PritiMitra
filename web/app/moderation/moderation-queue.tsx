"use client";

import { useCallback, useEffect, useState } from "react";

type ReportStatus = "PENDING" | "ACTIONED" | "DISMISSED";
type ReportItem = {
  reportId: string;
  targetType: "PROFILE" | "MESSAGE" | "COMMUNITY_POST";
  targetPublicId: string | null;
  category: string;
  details: string | null;
  status: ReportStatus;
  createdAt: string;
  reportedProfilePublicId: string | null;
  reportedDisplayName: string;
  evidence: string;
};
type ReportPage = { items: ReportItem[]; nextCursor: string | null; hasMore: boolean };
type Payload = ReportPage & { detail?: string; message?: string };

const FILTERS: ReportStatus[] = ["PENDING", "ACTIONED", "DISMISSED"];
const LABELS: Record<string, string> = {
  DISMISS: "Dismiss report",
  HIDE_CONTENT: "Hide community post",
  SUSPEND_ACCOUNT: "Suspend account",
};

export function ModerationQueue() {
  const [status, setStatus] = useState<ReportStatus>("PENDING");
  const [items, setItems] = useState<ReportItem[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async (cursor?: string, append = false) => {
    setLoading(true);
    setError(null);
    setNotice(null);
    try {
      const query = new URLSearchParams({ status, size: "20" });
      if (cursor) query.set("cursor", cursor);
      const response = await fetch(`/api/backend/v1/moderation/reports?${query.toString()}`, {
        cache: "no-store",
      });
      const payload = (await response.json().catch(() => null)) as Payload | null;
      if (!response.ok) {
        const message = payload?.detail ?? payload?.message;
        throw new Error(message || `Unable to load reports (HTTP ${response.status})`);
      }
      const page = payload as ReportPage;
      setItems((current) => append ? [...current, ...page.items] : page.items);
      setNextCursor(page.nextCursor);
      setHasMore(page.hasMore);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Unable to load the moderation queue");
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => {
    void load();
  }, [load]);

  async function review(report: ReportItem, decision: "DISMISS" | "HIDE_CONTENT" | "SUSPEND_ACCOUNT") {
    if (decision === "SUSPEND_ACCOUNT" && !window.confirm("Suspend this account and close its active matches and Blind Date sessions?")) {
      return;
    }
    if (decision === "HIDE_CONTENT" && !window.confirm("Hide this community post from members?")) {
      return;
    }
    setBusyId(report.reportId);
    setError(null);
    setNotice(null);
    try {
      const response = await fetch(`/api/backend/v1/moderation/reports/${report.reportId}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ decision, note: notes[report.reportId] ?? "" }),
      });
      const payload = (await response.json().catch(() => null)) as { detail?: string; message?: string; status?: string } | null;
      if (!response.ok) {
        const message = payload?.detail ?? payload?.message;
        throw new Error(message || `Unable to review report (HTTP ${response.status})`);
      }
      setNotice(`Report ${decision === "DISMISS" ? "dismissed" : "actioned"}.`);
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Unable to review report");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <main className="moderation-page">
      <header className="moderation-header">
        <div>
          <p className="moderation-eyebrow">PRITIMITRA · OPERATIONS</p>
          <h1>Trust &amp; Safety</h1>
          <p>Private report review. Reporter identities are intentionally not shown in this queue.</p>
        </div>
        <span className="moderation-count">{items.length} loaded</span>
      </header>

      <nav className="moderation-tabs" aria-label="Report status">
        {FILTERS.map((filter) => (
          <button key={filter} type="button" className={filter === status ? "is-active" : ""}
            aria-pressed={filter === status} onClick={() => { setLoading(true); setStatus(filter); }}>
            {filter === "PENDING" ? "Needs review" : filter === "ACTIONED" ? "Actioned" : "Dismissed"}
          </button>
        ))}
      </nav>

      {error && <div className="moderation-alert" role="alert">{error}</div>}
      {notice && <div className="moderation-notice" role="status">{notice}</div>}
      {loading && <p className="moderation-state">Loading reports…</p>}
      {!loading && !error && items.length === 0 && (
        <section className="moderation-empty">
          <h2>No {status.toLowerCase()} reports</h2>
          <p>This queue is clear for the selected status.</p>
        </section>
      )}

      <section className="moderation-list" aria-label="Reports">
        {items.map((report) => (
          <article className="moderation-card" key={report.reportId}>
            <div className="moderation-card-heading">
              <div>
                <p className="moderation-eyebrow">{report.targetType.replaceAll("_", " ")} · {report.category.replaceAll("_", " ")}</p>
                <h2>{report.reportedDisplayName || "Unavailable account"}</h2>
                <p className="moderation-muted">{new Date(report.createdAt).toLocaleString()}</p>
              </div>
              <span className={`moderation-status status-${report.status.toLowerCase()}`}>{report.status}</span>
            </div>
            {report.details && <blockquote className="moderation-reason">{report.details}</blockquote>}
            {report.evidence && (
              <div className="moderation-evidence">
                <p>Reported content / profile context</p>
                <div>{report.evidence}</div>
              </div>
            )}
            <p className="moderation-target">
              Target reference: <code>{report.targetPublicId ?? "Target no longer available"}</code>
            </p>
            {status === "PENDING" && (
              <div className="moderation-review-controls">
                <label htmlFor={`note-${report.reportId}`}>Internal review note (optional)</label>
                <textarea id={`note-${report.reportId}`} value={notes[report.reportId] ?? ""}
                  maxLength={500} rows={2} onChange={(event) => setNotes((current) => ({
                    ...current, [report.reportId]: event.target.value,
                  }))} placeholder="Record the decision rationale…" />
                <div className="moderation-actions">
                  <button type="button" className="secondary" disabled={busyId === report.reportId}
                    onClick={() => void review(report, "DISMISS")}>{LABELS.DISMISS}</button>
                  {report.targetType === "COMMUNITY_POST" && (
                    <button type="button" className="secondary" disabled={busyId === report.reportId}
                      onClick={() => void review(report, "HIDE_CONTENT")}>{LABELS.HIDE_CONTENT}</button>
                  )}
                  <button type="button" className="danger" disabled={busyId === report.reportId}
                    onClick={() => void review(report, "SUSPEND_ACCOUNT")}>
                    {busyId === report.reportId ? "Saving…" : LABELS.SUSPEND_ACCOUNT}
                  </button>
                </div>
              </div>
            )}
          </article>
        ))}
      </section>

      {!loading && hasMore && nextCursor && (
        <button className="moderation-load-more" type="button" onClick={() => void load(nextCursor, true)}>
          Load more reports
        </button>
      )}
    </main>
  );
}
