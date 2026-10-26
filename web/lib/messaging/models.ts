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

/** History is newest-first; the UI reverses it for display. latestCursor is set on the first (newest) page only. */
export type MessagePage = {
  items: MessageSummary[];
  nextCursor: string | null;
  latestCursor: string | null;
};

/** Catch-up page: ascending messages newer than the requested cursor. cursor advances only through returned items. */
export type CatchUpPage = {
  items: MessageSummary[];
  cursor: string;
  hasMore: boolean;
};

/** Conversation detail, including the other participant's public profile id for blocking. */
export type ConversationDetail = {
  conversationId: string;
  status: "ACTIVE" | "UNMATCHED";
  otherProfilePublicId: string | null;
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
  const latestCursor = nullableString(value.latestCursor);
  return nextCursor === undefined || latestCursor === undefined ? null : { items, nextCursor, latestCursor };
}

/** Returns a catch-up page, or null when the payload does not match the contract. */
export function parseCatchUpPage(value: unknown): CatchUpPage | null {
  if (!isObject(value) || !Array.isArray(value.items) || typeof value.cursor !== "string" || typeof value.hasMore !== "boolean") {
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
  return { items, cursor: value.cursor, hasMore: value.hasMore };
}

/** Returns conversation detail, or null when the payload does not match the contract. */
export function parseConversationDetail(value: unknown): ConversationDetail | null {
  if (!isObject(value) || typeof value.conversationId !== "string") {
    return null;
  }
  const { status } = value;
  const otherProfilePublicId = nullableString(value.otherProfilePublicId);
  if ((status !== "ACTIVE" && status !== "UNMATCHED") || otherProfilePublicId === undefined) {
    return null;
  }
  return { conversationId: value.conversationId, status, otherProfilePublicId };
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
