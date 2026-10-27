"use client";

import { useCallback, useEffect, useState } from "react";

type QueueStatus = "PENDING" | "ACTIONED" | "DISMISSED" | "ALL";
type Decision = "DISMISS" | "HIDE_POST" | "HIDE_PROFILE" | "SUSPEND_ACCOUNT";
type Report = {
  reportId: string;
  targetType: "PROFILE" | "MESSAGE" | "COMMUNITY_POST";
  targetPublicId: string | null;
  reporterProfilePublicId: string | null;
  reportedProfilePublicId: string | null;
  category: string;
  details: string | null;
  targetPreview: string | null;
  status: string;
  createdAt: string;
  reviewDecision: string | null;
  reviewNote: string | null;
  reviewedAt: string | null;
  reviewerDisplayName: string | null;
};
type Page = { items: Report[]; nextCursor: string | null };

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
function parsePage(value: unknown): Page {
  if (!isRecord(value) || !Array.isArray(value.items) ||
      !(value.nextCursor === null || typeof value.nextCursor === "string")) {
    throw new Error("The moderation response was invalid.");
  }
  const items = (value.items as unknown[]).filter((item): item is Report =>
    isRecord(item) && typeof item.reportId === "string" &&
    (item.targetType === "PROFILE" || item.targetType === "MESSAGE" || item.targetType === "COMMUNITY_POST") &&
    typeof item.category === "string" && typeof item.status === "string" &&
    typeof item.createdAt === "string" &&
    (item.targetPublicId === null || typeof item.targetPublicId === "string") &&
    (item.reporterProfilePublicId === null || typeof item.reporterProfilePublicId === "string") &&
    (item.reportedProfilePublicId === null || typeof item.reportedProfilePublicId === "string") &&
    (item.details === null || typeof item.details === "string") &&
    (item.targetPreview === null || typeof item.targetPreview === "string") &&
    (item.reviewDecision === null || typeof item.reviewDecision === "string") &&
    (item.reviewNote === null || typeof item.reviewNote === "string") &&
    (item.reviewedAt === null || typeof item.reviewedAt === "string") &&
    (item.reviewerDisplayName === null || typeof item.reviewerDisplayName === "string"));
  return { items, nextCursor: value.nextCursor };
}
function dateLabel(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "Date unavailable"
    : new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(date);
}

async function api<T>(path: string, method = "GET", payload?: unknown): Promise<T> {
  const response = await fetch(path, {
    method,
    cache: "no-store",
    headers: payload === undefined ? undefined : { "Content-Type": "application/json" },
    body: payload === undefined ? undefined : JSON.stringify(payload),
  });
  if (response.status === 401) throw new Error("Your session has expired. Sign in again.");
  if (response.status === 403) throw new Error("Moderator access is required. Ask an administrator to assign the PritiMitraModerators role.");
  if (!response.ok) throw new Error("The request could not be completed. Please try again.");
  if (response.status === 204) return undefined as T;
  return await response.json() as T;
}

export function ModerationQueue() {
  const [reports, setReports] = useState<Report[]>([]);
  const [status, setStatus] = useState<QueueStatus>("PENDING");
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async (filter: QueueStatus, cursor?: string | null) => {
    setLoading(true);
    setError(null);
    const query = new URLSearchParams({ status: filter, size: "20" });
    if (cursor) query.set("cursor", cursor);
    try {
      const page = parsePage(await api<unknown>("/api/backend/v1/moderation/reports?" + query.toString()));
      setReports((current) => cursor ? [...current, ...page.items] : page.items);
      setNextCursor(page.nextCursor);
    } catch (failure) {
      setReports([]);
      setNextCursor(null);
      setError(failure instanceof Error ? failure.message : "The moderation queue is unavailable.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    void Promise.resolve().then(() => { if (!cancelled) void load(status); });
    return () => { cancelled = true; };
  }, [load, status]);

  async function review(report: Report, decision: Decision) {
    const note = (notes[report.reportId] ?? "").trim();
    if (decision === "SUSPEND_ACCOUNT" && !note) {
      setError("Add a moderator note before suspending an account.");
      return;
    }
    if (decision === "SUSPEND_ACCOUNT" &&
        !window.confirm("Suspend this account? The account will lose access until an administrator restores it.")) {
      return;
    }
    setBusyId(report.reportId);
    setError(null);
    setNotice(null);
    try {
      await api("/api/backend/v1/moderation/reports/" + report.reportId + "/decision", "PUT", {
        decision,
        note: note || null,
      });
      setNotice("Report reviewed: " + decision.replaceAll("_", " ").toLowerCase() + ".");
      await load(status);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "The report could not be reviewed.");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <section className="moderation-workspace" aria-labelledby="moderation-reports-title">
      <div className="moderation-toolbar">
        <div>
          <h2 id="moderation-reports-title">Reports</h2>
          <p>Reporter notes and reported content are private. Review decisions are recorded for audit.</p>
        </div>
        <label className="moderation-filter">
          <span>Status</span>
          <select value={status} onChange={(event) => setStatus(event.target.value as QueueStatus)}>
            <option value="PENDING">Pending</option>
            <option value="ACTIONED">Actioned</option>
            <option value="DISMISSED">Dismissed</option>
            <option value="ALL">All reports</option>
          </select>
        </label>
      </div>

      {error ? <p className="moderation-alert" role="alert">{error}</p> : null}
      {notice ? <p className="moderation-notice" role="status">{notice}</p> : null}
      {loading && reports.length === 0 ? <p role="status">Loading reports…</p> :
        reports.length === 0 ? <div className="moderation-empty"><h3>No reports here</h3><p>There are no reports for this status.</p></div> :
        <div className="moderation-report-list">
          {reports.map((report) => (
            <article className="moderation-report-card" key={report.reportId}>
              <div className="moderation-card-top">
                <div>
                  <p className="moderation-eyebrow">{report.targetType.replaceAll("_", " ")} · {report.category.replaceAll("_", " ")}</p>
                  <h3>Report {report.reportId.slice(0, 8)}</h3>
                </div>
                <span className={"moderation-status moderation-status-" + report.status.toLowerCase()}>{report.status}</span>
              </div>
              <p className="moderation-meta">Submitted {dateLabel(report.createdAt)}</p>
              <div className="moderation-content">
                <p className="moderation-content-label">Reported content or profile</p>
                <p>{report.targetPreview || "The target content is no longer available."}</p>
                {report.targetPublicId ? <small>Target reference: {report.targetPublicId}</small> : null}
              </div>
              {report.details ? <div className="moderation-reporter-note"><p className="moderation-content-label">Reporter note</p><p>{report.details}</p></div> : null}
              {report.status !== "PENDING" ? (
                <div className="moderation-reviewed">
                  <p><strong>Decision:</strong> {report.reviewDecision || "Recorded"}</p>
                  {report.reviewNote ? <p><strong>Moderator note:</strong> {report.reviewNote}</p> : null}
                  <p className="moderation-meta">Reviewed {report.reviewedAt ? dateLabel(report.reviewedAt) : "at an unknown time"} by {report.reviewerDisplayName || "Moderator"}</p>
                </div>
              ) : (
                <div className="moderation-actions">
                  <label className="moderation-note-field">
                    <span>Moderator note (required to suspend an account)</span>
                    <textarea maxLength={500} rows={2} value={notes[report.reportId] ?? ""}
                      onChange={(event) => setNotes((current) => ({ ...current, [report.reportId]: event.target.value }))}
                      placeholder="Record the reason for your decision…" />
                  </label>
                  <div className="moderation-button-row">
                    <button type="button" className="moderation-button" disabled={busyId !== null}
                      onClick={() => void review(report, "DISMISS")}>Dismiss</button>
                    {report.targetType === "COMMUNITY_POST" ? (
                      <button type="button" className="moderation-button moderation-button-primary" disabled={busyId !== null}
                        onClick={() => void review(report, "HIDE_POST")}>Hide post</button>
                    ) : null}
                    {report.targetType === "PROFILE" ? (
                      <button type="button" className="moderation-button moderation-button-primary" disabled={busyId !== null}
                        onClick={() => void review(report, "HIDE_PROFILE")}>Hide profile</button>
                    ) : null}
                    <button type="button" className="moderation-button moderation-button-danger" disabled={busyId !== null}
                      onClick={() => void review(report, "SUSPEND_ACCOUNT")}>
                      {busyId === report.reportId ? "Saving…" : "Suspend account"}
                    </button>
                  </div>
                </div>
              )}
            </article>
          ))}
        </div>}
      {nextCursor && !loading ? <div className="moderation-load-more">
        <button type="button" className="moderation-button" onClick={() => void load(status, nextCursor)}>Load more reports</button>
      </div> : null}
    </section>
  );
}
