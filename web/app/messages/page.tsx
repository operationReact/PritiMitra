import Link from "next/link";
import { Suspense } from "react";
import { loadProtectedJson } from "@/lib/profile/server";
import { parseConversationPage } from "@/lib/messaging/models";

export const metadata = {
  title: "Messages",
};

export default function MessagesPage() {
  return (
    <main className="app-page">
      <h1>Messages</h1>
      <Suspense fallback={<p>Loading messages…</p>}>
        <ConversationList />
      </Suspense>
    </main>
  );
}

async function ConversationList() {
  const load = await loadProtectedJson(["v1", "conversations"]);
  if (load.kind === "unavailable") {
    return <p role="alert">Messages are temporarily unavailable. Please try again.</p>;
  }
  if (load.kind === "signed-out") {
    return <p>Please sign in to see your messages.</p>;
  }
  if (load.kind === "not-eligible") {
    return <p>Messaging is available once your account is verified and your profile is complete.</p>;
  }

  const page = parseConversationPage(load.body);
  if (!page) {
    return <p role="alert">Messages are temporarily unavailable. Please try again.</p>;
  }
  if (page.items.length === 0) {
    return <p>No conversations yet. Mutual likes start a conversation.</p>;
  }

  return (
    <ul className="conversation-list">
      {page.items.map((conversation) => (
        <li key={conversation.conversationId}>
          <Link className="conversation-link" href={`/messages/${encodeURIComponent(conversation.conversationId)}`}>
            <span className="conversation-preview">{conversation.lastMessagePreview ?? "No messages yet"}</span>
            <span className="conversation-meta">
              {conversation.status === "UNMATCHED" ? "Unmatched · read only" : null}
              {conversation.unreadCount > 0 ? ` · ${conversation.unreadCount} unread` : null}
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
