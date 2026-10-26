/** Mirrors ConversationDtos.ConversationSummary. Identifiers are opaque public IDs. */
export type ConversationSummary = {
  conversationId: string;
  matchId: string;
  status: "ACTIVE" | "UNMATCHED";
  lastMessagePreview: string | null;
  lastMessageAt: string | null;
  unreadCount: number;
};

export type ConversationPage = {
  items: ConversationSummary[];
  nextCursor: string | null;
};

/** Mirrors ConversationDtos.MessageSummary. clientMessageId is present only on the viewer's own messages. */
export type MessageSummary = {
  messageId: string;
  clientMessageId: string | null;
  body: string;
  mine: boolean;
  createdAt: string;
};

/** History is newest-first; the UI reverses it for display. */
export type MessagePage = {
  items: MessageSummary[];
  nextCursor: string | null;
};

/** Maximum text length accepted by the backend (ConversationService). */
export const MAX_MESSAGE_LENGTH = 2000;

type Json = Record<string, unknown>;

function isObject(value: unknown): value is Json {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function nullableString(value: unknown): string | null | undefined {
  if (value === null || value === undefined) {
    return null;
  }
  return typeof value === "string" ? value : undefined;
}

function parseConversation(value: unknown): ConversationSummary | null {
  if (!isObject(value)) {
    return null;
  }
  const { conversationId, matchId, status, unreadCount } = value;
  const lastMessagePreview = nullableString(value.lastMessagePreview);
  const lastMessageAt = nullableString(value.lastMessageAt);
  if (
    typeof conversationId !== "string" ||
    typeof matchId !== "string" ||
    (status !== "ACTIVE" && status !== "UNMATCHED") ||
    typeof unreadCount !== "number" ||
    lastMessagePreview === undefined ||
    lastMessageAt === undefined
  ) {
    return null;
  }
  return { conversationId, matchId, status, lastMessagePreview, lastMessageAt, unreadCount };
}

/** Returns a typed conversation page, or null when the payload does not match the contract. */
export function parseConversationPage(value: unknown): ConversationPage | null {
  if (!isObject(value) || !Array.isArray(value.items)) {
    return null;
  }
  const items: ConversationSummary[] = [];
  for (const item of value.items) {
    const parsed = parseConversation(item);
    if (!parsed) {
      return null;
    }
    items.push(parsed);
  }
  const nextCursor = nullableString(value.nextCursor);
  return nextCursor === undefined ? null : { items, nextCursor };
}

/** Returns a typed message, or null when the payload does not match the contract. */
export function parseMessage(value: unknown): MessageSummary | null {
  if (!isObject(value)) {
    return null;
  }
  const { messageId, body, mine, createdAt } = value;
  const clientMessageId = nullableString(value.clientMessageId);
  if (
    typeof messageId !== "string" ||
    typeof body !== "string" ||
    typeof mine !== "boolean" ||
    typeof createdAt !== "string" ||
    clientMessageId === undefined
  ) {
    return null;
  }
  return { messageId, clientMessageId, body, mine, createdAt };
}

/** Returns a typed message page, or null when the payload does not match the contract. */
export function parseMessagePage(value: unknown): MessagePage | null {
  if (!isObject(value) || !Array.isArray(value.items)) {
    return null;
  }
  const items: MessageSummary[] = [];
  for (const item of value.items) {
    const parsed = parseMessage(item);
    if (!parsed) {
      return null;
    }
    items.push(parsed);
  }
  const nextCursor = nullableString(value.nextCursor);
  return nextCursor === undefined ? null : { items, nextCursor };
}

/** Client-side check mirroring the backend limit. Blank messages are rejected before any request is sent. */
export function validateMessageBody(raw: string): string | null {
  const trimmed = raw.trim();
  if (trimmed.length === 0) {
    return "Write a message before sending.";
  }
  if (trimmed.length > MAX_MESSAGE_LENGTH) {
    return `Messages can be up to ${MAX_MESSAGE_LENGTH} characters.`;
  }
  return null;
}
