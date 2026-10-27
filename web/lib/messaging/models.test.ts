import { describe, expect, it } from "vitest";
import {
  MAX_MESSAGE_LENGTH,
  parseCatchUpPage,
  parseConversationDetail,
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

  it("parses latestCursor and rejects a non-string value", () => {
    expect(parseMessagePage({ items: [message], nextCursor: null, latestCursor: "abc" })?.latestCursor).toBe("abc");
    expect(parseMessagePage({ items: [message], nextCursor: null, latestCursor: null })?.latestCursor).toBeNull();
    expect(parseMessagePage({ items: [message], nextCursor: null, latestCursor: 7 })).toBeNull();
  });
});

describe("parseCatchUpPage", () => {
  it("accepts a catch-up page with more messages pending", () => {
    expect(parseCatchUpPage({ items: [message], cursor: "c1", hasMore: true })).toEqual({
      items: [message],
      cursor: "c1",
      hasMore: true,
    });
  });

  it("accepts an empty page that echoes the existing cursor", () => {
    expect(parseCatchUpPage({ items: [], cursor: "c1", hasMore: false })).toEqual({
      items: [],
      cursor: "c1",
      hasMore: false,
    });
  });

  it("rejects a page without a string cursor or a boolean hasMore", () => {
    expect(parseCatchUpPage({ items: [], cursor: null, hasMore: false })).toBeNull();
    expect(parseCatchUpPage({ items: [], cursor: "c1", hasMore: "no" })).toBeNull();
  });

  it("rejects a page when any item is malformed", () => {
    expect(parseCatchUpPage({ items: [{ body: 1 }], cursor: "c1", hasMore: false })).toBeNull();
  });
});

describe("parseConversationDetail", () => {
  it("accepts an active detail with the other participant's public profile ID", () => {
    expect(
      parseConversationDetail({
        conversationId: conversation.conversationId,
        status: "ACTIVE",
        otherProfilePublicId: "profile-1",
      }),
    ).toEqual({ conversationId: conversation.conversationId, status: "ACTIVE", otherProfilePublicId: "profile-1" });
  });

  it("accepts a null other profile ID for an unmatched conversation", () => {
    expect(
      parseConversationDetail({ conversationId: conversation.conversationId, status: "UNMATCHED", otherProfilePublicId: null })?.status,
    ).toBe("UNMATCHED");
  });

  it("rejects an unknown status or a non-string profile ID", () => {
    expect(
      parseConversationDetail({ conversationId: conversation.conversationId, status: "DELETED", otherProfilePublicId: null }),
    ).toBeNull();
    expect(
      parseConversationDetail({ conversationId: conversation.conversationId, status: "ACTIVE", otherProfilePublicId: 5 }),
    ).toBeNull();
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
