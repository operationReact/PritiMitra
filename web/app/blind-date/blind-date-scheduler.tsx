"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";

type Booking = {
  optInId: string;
  startsAt: string;
  timeZone: string;
  status: "WAITING" | "PAIRED" | "WITHDRAWN";
  sessionId: string | null;
  partnerProfilePublicId: string | null;
  sessionStatus: string | null;
  createdAt: string;
};
type BookingList = { items: unknown[] };

async function api<T>(path: string, method = "GET", payload?: unknown): Promise<T> {
  const response = await fetch(path, {
    method, cache: "no-store",
    headers: payload === undefined ? undefined : { "Content-Type": "application/json" },
    body: payload === undefined ? undefined : JSON.stringify(payload),
  });
  if (!response.ok) throw new Error(response.status === 401 ? "Your session has expired. Please sign in again." : "The request could not be completed. Check the selected time and try again.");
  if (response.status === 204) return undefined as T;
  return await response.json() as T;
}
function localInput(d: Date): string {
  const pad = (value: number) => String(value).padStart(2, "0");
  return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()) + "T" + pad(d.getHours()) + ":" + pad(d.getMinutes());
}
function defaultSlot(): string {
  const date = new Date(Date.now() + 60 * 60 * 1000);
  date.setMinutes(Math.ceil(date.getMinutes() / 15) * 15, 0, 0);
  return localInput(date);
}
function parseBookings(value: unknown): Booking[] {
  if (typeof value !== "object" || value === null || !Array.isArray((value as BookingList).items)) throw new Error("Your schedule could not be read.");
  return (value as BookingList).items.filter((item): item is Booking => {
    if (typeof item !== "object" || item === null) return false;
    const booking = item as Record<string, unknown>;
    return typeof booking.optInId === "string" && typeof booking.startsAt === "string" &&
      typeof booking.timeZone === "string" && typeof booking.status === "string" &&
      (booking.sessionId === null || typeof booking.sessionId === "string") &&
      (booking.partnerProfilePublicId === null || typeof booking.partnerProfilePublicId === "string") &&
      (booking.sessionStatus === null || typeof booking.sessionStatus === "string") &&
      typeof booking.createdAt === "string";
  });
}
function formatTime(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? "Date unavailable" :
    new Intl.DateTimeFormat(undefined, { dateStyle: "full", timeStyle: "short" }).format(date);
}

export function BlindDateScheduler() {
  const [slot, setSlot] = useState(defaultSlot);
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      setBookings(parseBookings(await api<unknown>("/api/backend/v1/blind-dates/my")));
      setError(null);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Your schedule is unavailable.");
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => { void Promise.resolve().then(refresh); }, [refresh]);

  async function optIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true); setError(null); setNotice(null);
    try {
      const date = new Date(slot);
      if (Number.isNaN(date.getTime())) throw new Error("Choose a valid time.");
      await api("/api/backend/v1/blind-dates/opt-ins", "POST", {
        startsAt: date.toISOString(),
        timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC",
      });
      setNotice("Your opt-in was saved. A session is scheduled only when another eligible person chooses the same slot.");
      await refresh();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Your opt-in could not be saved.");
    } finally {
      setSaving(false);
    }
  }
  async function withdraw(item: Booking) {
    setSaving(true); setError(null); setNotice(null);
    try {
      if (item.status === "PAIRED" && item.sessionId && item.sessionStatus === "SCHEDULED") {
        await api("/api/backend/v1/blind-dates/sessions/" + item.sessionId, "DELETE");
        setNotice("The scheduled session was cancelled.");
      } else {
        await api("/api/backend/v1/blind-dates/opt-ins/" + item.optInId, "DELETE");
        setNotice("Your opt-in was withdrawn.");
      }
      await refresh();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "The schedule could not be updated.");
    } finally {
      setSaving(false);
    }
  }
  const [timeBounds] = useState(() => ({
    minimum: localInput(new Date(Date.now() + 16 * 60 * 1000)),
    maximum: localInput(new Date(Date.now() + 14 * 24 * 60 * 60 * 1000)),
  }));

  return (
    <div className="phase6-layout">
      <section className="phase6-panel">
        <p className="phase6-eyebrow">Opt in when you are ready</p><h2>Choose a time slot</h2>
        <p className="phase6-muted">Choose a 15-minute boundary in the next 14 days. Pairing requires two eligible, unblocked people to choose the same time independently.</p>
        <form className="phase6-post-form" onSubmit={optIn}>
          <label htmlFor="blind-date-slot">Your local start time</label>
          <input id="blind-date-slot" type="datetime-local" required min={timeBounds.minimum} max={timeBounds.maximum} step={900}
            value={slot} onChange={(event) => setSlot(event.target.value)} />
          <button className="phase6-primary" disabled={saving}>{saving ? "Saving…" : "Opt in to this slot"}</button>
        </form>
        <p className="phase6-muted">Current time zone: {Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC"}. Opt-in never creates a dating match or conversation automatically.</p>
      </section>
      <section className="phase6-panel">
        <div className="phase6-section-heading"><div><p className="phase6-eyebrow">Your choice, your schedule</p><h2>My Blind Date slots</h2></div>
          <button className="phase6-secondary" onClick={() => void refresh()} disabled={loading}>Refresh</button></div>
        {loading ? <p role="status">Loading your schedule…</p> :
          bookings.length === 0 ? <p className="phase6-muted">You have not opted into a slot yet.</p> :
          <ul className="phase6-booking-list">{bookings.map((item) => (
            <li className="phase6-booking" key={item.optInId}>
              <div><strong>{formatTime(item.startsAt)}</strong><p className="phase6-muted">{item.timeZone}</p>
                <span className={"phase6-status phase6-status-" + item.status.toLowerCase()}>
                  {item.status === "PAIRED" && item.sessionStatus === "SCHEDULED" ? "Scheduled" :
                    item.status === "WAITING" ? "Waiting for a partner" :
                    item.sessionStatus === "CANCELLED" ? "Cancelled" : item.status}
                </span>
                {item.partnerProfilePublicId && item.sessionStatus === "SCHEDULED" ?
                  <p className="phase6-muted">Partner profile ID: {item.partnerProfilePublicId}</p> : null}
              </div>
              {(item.status === "WAITING" || (item.status === "PAIRED" && item.sessionStatus === "SCHEDULED" && new Date(item.startsAt) > new Date())) ?
                <button className="phase6-secondary" disabled={saving} onClick={() => void withdraw(item)}>
                  {item.status === "WAITING" ? "Withdraw" : "Cancel session"}
                </button> : null}
            </li>
          ))}</ul>}
      </section>
      {error ? <p className="phase6-error" role="alert">{error}</p> : null}
      {notice ? <p className="phase6-notice" role="status">{notice}</p> : null}
    </div>
  );
}
