"use client";

import { useCallback, useEffect, useState } from "react";

type AuditEvent = {
  eventId: string;
  actorReference: string;
  eventType: string;
  reportPublicId: string;
  targetType: string;
  targetPublicId: string | null;
  note: string | null;
  createdAt: string;
};
type Page = { items: AuditEvent[]; nextCursor: string | null; from: string; to: string };
type Filters = { eventType: string; from: string; to: string };

const DEFAULT_FILTERS: Filters = { eventType: "ALL", from: "", to: "" };

const EVENTS = [
  ["ALL", "All events"],
  ["REPORT_DISMISSED", "Report dismissed"],
  ["COMMUNITY_POST_HIDDEN", "Community post hidden"],
  ["PROFILE_HIDDEN", "Profile hidden"],
  ["ACCOUNT_SUSPENDED", "Account suspended"],
] as const;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function parsePage(value: unknown): Page {
  if (!isRecord(value) || !Array.isArray(value.items) ||
      !(value.nextCursor === null || value.nextCursor === undefined || typeof value.nextCursor === "string") ||
      typeof value.from !== "string" || typeof value.to !== "string") {
    throw new Error("The audit-history response was invalid.");
  }
  const items = (value.items as unknown[]).filter((item): item is AuditEvent =>
    isRecord(item) && typeof item.eventId === "string" &&
    typeof item.actorReference === "string" && typeof item.eventType === "string" &&
    typeof item.reportPublicId === "string" && typeof item.targetType === "string" &&
    (item.targetPublicId === null || item.targetPublicId === undefined || typeof item.targetPublicId === "string") &&
    (item.note === null || item.note === undefined || typeof item.note === "string") &&
    typeof item.createdAt === "string");
  return { items, nextCursor: typeof value.nextCursor === "string" ? value.nextCursor : null, from: value.from, to: value.to };
}

function dateLabel(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? "Date unavailable"
    : new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(date);
}

function dateStart(value: string): string | null {
  return value ? new Date(value + "T00:00:00.000Z").toISOString() : null;
}
function dateEnd(value: string): string | null {
  if (!value) return null;
  const now = new Date();
  if (value === now.toISOString().slice(0, 10)) return null;
  return new Date(value + "T23:59:59.999Z").toISOString();
}
function queryFor(filters: Filters, cursor?: string | null): string {
  const query = new URLSearchParams({ eventType: filters.eventType, size: "20" });
  const from = dateStart(filters.from);
  const to = dateEnd(filters.to);
  if (from) query.set("from", from);
  if (to) query.set("to", to);
  if (cursor) query.set("cursor", cursor);
  return query.toString();
}

export function ModerationAuditHistory() {
  const [filters, setFilters] = useState<Filters>(DEFAULT_FILTERS);
  const [applied, setApplied] = useState<Filters>(DEFAULT_FILTERS);
  const [events, setEvents] = useState<AuditEvent[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [range, setRange] = useState<{ from: string; to: string } | null>(null);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async (nextFilters: Filters, cursor?: string | null) => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch("/api/backend/v1/moderation/audit?" + queryFor(nextFilters, cursor), {
        cache: "no-store",
      });
      if (response.status === 401) throw new Error("Your session has expired. Sign in again.");
      if (response.status === 403) throw new Error("Moderator access is required for audit history.");
      if (!response.ok) {
        const body: unknown = await response.json().catch(() => null);
        const message = isRecord(body) && typeof body.message === "string" ? body.message : null;
        throw new Error(message ?? "Audit history could not be loaded. Please try again.");
      }
      const page = parsePage(await response.json());
      setEvents((current) => cursor ? [...current, ...page.items] : page.items);
      setNextCursor(page.nextCursor);
      setRange({ from: page.from, to: page.to });
      setApplied(nextFilters);
    } catch (failure) {
      if (!cursor) setEvents([]);
      setNextCursor(null);
      setError(failure instanceof Error ? failure.message : "Audit history is unavailable.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    void Promise.resolve().then(() => { if (!cancelled) void load(DEFAULT_FILTERS); });
    return () => { cancelled = true; };
  }, [load]);

  async function exportCsv() {
    setExporting(true);
    setError(null);
    setNotice(null);
    const query = new URLSearchParams({ eventType: applied.eventType });
    const from = dateStart(applied.from);
    const to = dateEnd(applied.to);
    if (from) query.set("from", from);
    if (to) query.set("to", to);
    try {
      const response = await fetch("/api/backend/v1/moderation/audit/export?" + query.toString(), {
        cache: "no-store",
      });
      if (response.status === 401) throw new Error("Your session has expired. Sign in again.");
      if (response.status === 403) throw new Error("Moderator access is required for audit export.");
      if (!response.ok) {
        const body: unknown = await response.json().catch(() => null);
        const message = isRecord(body) && typeof body.message === "string" ? body.message : null;
        throw new Error(message ?? "Audit export could not be completed. Narrow the date range and try again.");
      }
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = "pritimitra-moderation-audit.csv";
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      setNotice("The filtered audit history has been exported.");
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Audit export is unavailable.");
    } finally {
      setExporting(false);
    }
  }

  return (
    <section className="moderation-workspace audit-workspace" aria-labelledby="audit-history-title">
      <div className="moderation-toolbar audit-toolbar">
        <div>
          <h2 id="audit-history-title">Audit history</h2>
          <p>Review moderation actions without exporting reporter notes or reported content.</p>
        </div>
        <button className="moderation-button moderation-button-primary" type="button" onClick={() => void exportCsv()} disabled={exporting || loading}>
          {exporting ? "Preparing export…" : "Export CSV"}
        </button>
      </div>

      <form className="audit-filter-bar" onSubmit={(event) => { event.preventDefault(); void load(filters); }}>
        <label>
          <span>Event type</span>
          <select value={filters.eventType} onChange={(event) => setFilters((current) => ({ ...current, eventType: event.target.value }))}>
            {EVENTS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </label>
        <label>
          <span>From date (UTC)</span>
          <input type="date" value={filters.from} onChange={(event) => setFilters((current) => ({ ...current, from: event.target.value }))} />
        </label>
        <label>
          <span>To date (UTC)</span>
          <input type="date" value={filters.to} onChange={(event) => setFilters((current) => ({ ...current, to: event.target.value }))} />
        </label>
        <button type="submit" className="moderation-button" disabled={loading}>Apply filters</button>
      </form>

      {range ? <p className="audit-range">Showing events from {dateLabel(range.from)} through {dateLabel(range.to)}. Maximum export range: 90 days.</p> : null}
      {error ? <p className="moderation-alert" role="alert">{error}</p> : null}
      {notice ? <p className="moderation-notice" role="status">{notice}</p> : null}

      {loading && events.length === 0 ? <p role="status">Loading audit history…</p> :
        events.length === 0 ? <div className="moderation-empty"><h3>No audit events found</h3><p>Try another event type or date range.</p></div> :
        <div className="audit-table-scroll">
          <table className="audit-table">
            <thead><tr><th scope="col">Event</th><th scope="col">Occurred</th><th scope="col">Moderator</th><th scope="col">Reference</th><th scope="col">Note</th></tr></thead>
            <tbody>
              {events.map((event) => (
                <tr key={event.eventId}>
                  <td>
                    <strong>{event.eventType.replaceAll("_", " ").toLowerCase()}</strong>
                    <small>Event {event.eventId.slice(0, 8)}</small>
                  </td>
                  <td>{dateLabel(event.createdAt)}</td>
                  <td><span className="audit-actor-chip">sha256:{event.actorReference.replace(/^sha256:/, "").slice(0, 12)}…</span></td>
                  <td>
                    <span>{event.targetType.replaceAll("_", " ")}</span>
                    <small>Report {event.reportPublicId.slice(0, 8)}</small>
                    {event.targetPublicId ? <small>Target {event.targetPublicId.slice(0, 8)}</small> : null}
                  </td>
                  <td className="audit-note-cell">{event.note || "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>}

      {nextCursor && !loading ? <div className="moderation-load-more">
        <button type="button" className="moderation-button" onClick={() => void load(applied, nextCursor)}>Load more events</button>
      </div> : null}
    </section>
  );
}
