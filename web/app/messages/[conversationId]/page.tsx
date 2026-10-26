import Link from "next/link";
import { Suspense } from "react";
import { notFound } from "next/navigation";
import { loadProtectedJson } from "@/lib/profile/server";
import { parseMessagePage } from "@/lib/messaging/models";
import { ConversationThread } from "./conversation-thread";

const PUBLIC_ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export const metadata = {
  title: "Conversation",
};

export default function ConversationPage({
  params,
}: {
  params: Promise<{ conversationId: string }>;
}) {
  return (
    <main className="app-page">
      <p>
        <Link href="/messages">← Messages</Link>
      </p>
      <h1>Conversation</h1>
      <Suspense fallback={<p>Loading messages…</p>}>
        <ConversationContent params={params} />
      </Suspense>
    </main>
  );
}

async function ConversationContent({ params }: { params: Promise<{ conversationId: string }> }) {
  const { conversationId } = await params;
  if (!PUBLIC_ID_PATTERN.test(conversationId)) {
    notFound();
  }

  const load = await loadProtectedJson(["v1", "conversations", conversationId, "messages"]);
  if (load.kind === "unavailable") {
    return <p role="alert">This conversation is temporarily unavailable. Reload the page to try again.</p>;
  }
  if (load.kind === "signed-out") {
    return <p>Please sign in to read this conversation.</p>;
  }
  if (load.kind === "not-eligible") {
    return <p>Messaging is available once your account is verified and your profile is complete.</p>;
  }

  const page = parseMessagePage(load.body);
  if (!page) {
    return <p role="alert">This conversation is temporarily unavailable. Reload the page to try again.</p>;
  }

  // The API returns newest-first; the thread displays oldest-first.
  return (
    <ConversationThread
      conversationId={conversationId}
      initialMessages={[...page.items].reverse()}
      initialCursor={page.nextCursor}
    />
  );
}
