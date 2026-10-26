"use client";

import { FormEvent, useEffect, useRef, useState } from "react";
import {
  MAX_MESSAGE_LENGTH,
  MessageSummary,
  parseMessage,
  parseMessagePage,
  validateMessageBody,
} from "@/lib/messaging/models";

const PAGE_SIZE = 50;

/** A composed message that the server has not yet confirmed. It is shown separately from confirmed history. */
type OutgoingMessage = {
  clientMessageId: string;
  body: string;
  state: "sending" | "failed";
  error: string | null;
};

function messagesPath(conversationId: string): string {
  return `/api/backend/v1/conversations/${encodeURIComponent(conversationId)}/messages`;
}

/** Reads the stable problem `code` from an error response, or null when the body is not parseable. */
async function readErrorCode(response: Response): Promise<string | null> {
  try {
    const body: unknown = await response.json();
    if (typeof body === "object" && body !== null && "code" in body && typeof body.code === "string") {
      return body.code;
    }
  } catch {
    return null;
  }
  return null;
}

function sendErrorMessage(status: number, code: string | null): string {
  if (status === 401) {
    return "Your session has expired. Please sign in again.";
  }
  if (code === "CONVERSATION_CLOSED") {
    return "This conversation is closed, so the message was not sent.";
  }
  if (code === "IDEMPOTENCY_CONFLICT") {
    return "This message could not be sent because of a conflicting earlier attempt.";
  }
  return "We could not send your message. Retry to try again.";
}

/** Adds a message once, keeping history in chronological order. */
function mergeMessage(current: MessageSummary[], incoming: MessageSummary): MessageSummary[] {
  return current.some((item) => item.messageId === incoming.messageId) ? current : [...current, incoming];
}

export function ConversationThread({
  conversationId,
  initialMessages,
  initialCursor,
}: {
  conversationId: string;
  initialMessages: MessageSummary[];
  initialCursor: string | null;
}) {
  const [messages, setMessages] = useState<MessageSummary[]>(initialMessages);
  const [nextCursor, setNextCursor] = useState<string | null>(initialCursor);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [draft, setDraft] = useState("");
  const [composeError, setComposeError] = useState<string | null>(null);
  const [outgoing, setOutgoing] = useState<OutgoingMessage[]>([]);
  const [closed, setClosed] = useState(false);
  const endRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "end" });
  }, [messages.length, outgoing.length]);

  async function loadOlder() {
    if (!nextCursor) {
      return;
    }
    setLoadingOlder(true);
    setLoadError(null);
    try {
      const params = new URLSearchParams({ size: String(PAGE_SIZE), cursor: nextCursor });
      const response = await fetch(`${messagesPath(conversationId)}?${params.toString()}`, {
        credentials: "same-origin",
      });
      const page = response.ok ? parseMessagePage(await response.json()) : null;
      if (!page) {
        setLoadError("We could not load older messages. Please try again.");
        return;
      }
      const older = [...page.items].reverse();
      setMessages((current) => [
        ...older.filter((item) => !current.some((existing) => existing.messageId === item.messageId)),
        ...current,
      ]);
      setNextCursor(page.nextCursor);
    } catch {
      setLoadError("We could not reach PritiMitra. Check your connection and try again.");
    } finally {
      setLoadingOlder(false);
    }
  }

  /** Sends an outgoing entry. Retries reuse the same clientMessageId so the server cannot store a duplicate. */
  async function transmit(entry: OutgoingMessage) {
    setOutgoing((current) =>
      current.map((item) =>
        item.clientMessageId === entry.clientMessageId ? { ...item, state: "sending", error: null } : item,
      ),
    );
    const markFailed = (error: string) =>
      setOutgoing((current) =>
        current.map((item) =>
          item.clientMessageId === entry.clientMessageId ? { ...item, state: "failed", error } : item,
        ),
      );
    try {
      const response = await fetch(messagesPath(conversationId), {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ clientMessageId: entry.clientMessageId, body: entry.body }),
      });
      if (response.ok) {
        const sent = parseMessage(await response.json());
        if (sent) {
          setMessages((current) => mergeMessage(current, sent));
          setOutgoing((current) => current.filter((item) => item.clientMessageId !== entry.clientMessageId));
          return;
        }
        markFailed("We could not confirm your message. Retry to check again.");
        return;
      }
      const code = await readErrorCode(response);
      if (code === "CONVERSATION_CLOSED") {
        setClosed(true);
      }
      markFailed(sendErrorMessage(response.status, code));
    } catch {
      markFailed("We could not reach PritiMitra. Retry when you are back online.");
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const problem = validateMessageBody(draft);
    setComposeError(problem);
    if (problem || closed) {
      return;
    }
    const entry: OutgoingMessage = {
      clientMessageId: crypto.randomUUID(),
      body: draft.trim(),
      state: "sending",
      error: null,
    };
    setOutgoing((current) => [...current, entry]);
    setDraft("");
    void transmit(entry);
  }

  return (
    <section className="thread" aria-label="Conversation">
      {loadError ? <p role="alert">{loadError}</p> : null}
      {messages.length === 0 && outgoing.length === 0 ? <p>No messages yet. Say hello.</p> : null}

      {nextCursor ? (
        <button type="button" className="button secondary" onClick={loadOlder} disabled={loadingOlder}>
          {loadingOlder ? "Loading…" : "Load older messages"}
        </button>
      ) : null}

      <div className="thread-messages" role="log" aria-live="polite">
        {messages.map((message) => (
          <div key={message.messageId} className={`bubble ${message.mine ? "bubble-mine" : "bubble-theirs"}`}>
            <p>{message.body}</p>
            <time dateTime={message.createdAt}>{new Date(message.createdAt).toLocaleString()}</time>
          </div>
        ))}
        {outgoing.map((entry) => (
          <div key={entry.clientMessageId} className="bubble bubble-mine bubble-outgoing">
            <p>{entry.body}</p>
            {entry.state === "sending" ? (
              <span className="muted">Sending…</span>
            ) : (
              <div className="bubble-failure">
                <span role="alert">{entry.error}</span>
                <button type="button" className="button secondary" onClick={() => transmit(entry)}>
                  Retry
                </button>
              </div>
            )}
          </div>
        ))}
        <div ref={endRef} />
      </div>

      <form className="composer" onSubmit={handleSubmit}>
        <label htmlFor="message-body">Message</label>
        <textarea
          id="message-body"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          maxLength={MAX_MESSAGE_LENGTH}
          rows={3}
          disabled={closed}
          aria-describedby={composeError ? "message-body-error" : undefined}
        />
        {composeError ? <p id="message-body-error" role="alert">{composeError}</p> : null}
        {closed ? <p className="muted">This conversation is closed. You can still read earlier messages.</p> : null}
        <button type="submit" className="button" disabled={closed}>
          Send
        </button>
      </form>
    </section>
  );
}
