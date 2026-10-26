import { describe, expect, it } from "vitest";
import {
  MAX_MESSAGE_LENGTH,
  parseConversationPage,
  parseMessage,
  parseMessagePage,
  validateMessageBody,
} from "./models";

const conversation = {
  conversationId: "7f1c2a3e-1111-4222-8333-944455556666",
  matchId: "7f1c2a3e-2222-4222-8333-944455556666",
  status: "ACTIVE",
  lastMessagePreview: "Hi there",
  lastMessageAt: "2025-01-01T10:00:00Z",
  unreadCount: 2,
};

const message = {
  messageId: "7f1c2a3e-3333-4222-8333-944455556666",
  clientMessageId: "7f1c2a3e-4444-4222-8333-944455556666",
  body: "Hello",
  mine: true,
  createdAt: "2025-01-01T10:00:00Z",
};

describe("parseConversationPage", () => {
  it("accepts a valid page with a null cursor", () => {
    expect(parseConversationPage({ items: [conversation], nextCursor: null })).toEqual({
      items: [conversation],
      nextCursor: null,
    });
  });

  it("rejects an unknown lifecycle status", () => {
    expect(parseConversationPage({ items: [{ ...conversation, status: "DELETED" }], nextCursor: null })).toBeNull();
  });

  it("rejects a payload without an items array", () => {
    expect(parseConversationPage({ nextCursor: null })).toBeNull();
  });
});

describe("parseMessage and parseMessagePage", () => {
  it("accepts a message with a null client message ID for the other participant", () => {
    expect(parseMessage({ ...message, mine: false, clientMessageId: null })?.clientMessageId).toBeNull();
  });

  it("rejects a message whose mine flag is not boolean", () => {
    expect(parseMessage({ ...message, mine: "yes" })).toBeNull();
  });

  it("rejects a page when any item is malformed", () => {
    expect(parseMessagePage({ items: [message, { body: 1 }], nextCursor: null })).toBeNull();
  });
});

describe("validateMessageBody", () => {
  it("rejects blank input after trimming", () => {
    expect(validateMessageBody("   \n ")).not.toBeNull();
  });

  it("accepts text at the limit and rejects text one character over", () => {
    expect(validateMessageBody("a".repeat(MAX_MESSAGE_LENGTH))).toBeNull();
    expect(validateMessageBody("a".repeat(MAX_MESSAGE_LENGTH + 1))).not.toBeNull();
  });
});
