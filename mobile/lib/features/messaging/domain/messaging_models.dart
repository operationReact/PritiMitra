// Mirrors the backend match, conversation, message and notification DTOs. Identifiers are server-issued;
// the client never invents a match, message or notification state.

/// Largest text body the server accepts, in Unicode code points.
const int maxMessageLength = 2000;

class MatchSummary {
  const MatchSummary({
    required this.matchPublicId,
    required this.status,
    required this.displayName,
    required this.available,
    required this.matchedAt,
  });

  factory MatchSummary.fromJson(Map<String, dynamic> json) {
    return MatchSummary(
      matchPublicId: json['matchPublicId'] as String,
      status: json['status'] as String? ?? '',
      displayName: json['displayName'] as String?,
      available: json['available'] as bool? ?? false,
      matchedAt: json['matchedAt'] as String?,
    );
  }

  final String matchPublicId;
  final String status;
  final String? displayName;
  final bool available;
  final String? matchedAt;
}

class MatchPage {
  const MatchPage({required this.items, required this.nextCursor});

  factory MatchPage.fromJson(Map<String, dynamic> json) {
    final raw = json['items'];
    return MatchPage(
      items: raw is List
          ? raw
                .whereType<Map<String, dynamic>>()
                .map(MatchSummary.fromJson)
                .toList(growable: false)
          : const <MatchSummary>[],
      nextCursor: json['nextCursor'] as String?,
    );
  }

  final List<MatchSummary> items;
  final String? nextCursor;
}

class ConversationSummary {
  const ConversationSummary({
    required this.conversationId,
    required this.matchId,
    required this.status,
    required this.lastMessagePreview,
    required this.lastMessageAt,
    required this.unreadCount,
  });

  factory ConversationSummary.fromJson(Map<String, dynamic> json) {
    return ConversationSummary(
      conversationId: json['conversationId'] as String,
      matchId: json['matchId'] as String? ?? '',
      status: json['status'] as String? ?? '',
      lastMessagePreview: json['lastMessagePreview'] as String?,
      lastMessageAt: json['lastMessageAt'] as String?,
      unreadCount: (json['unreadCount'] as num?)?.toInt() ?? 0,
    );
  }

  final String conversationId;
  final String matchId;
  final String status;
  final String? lastMessagePreview;
  final String? lastMessageAt;
  final int unreadCount;
}

class ConversationPage {
  const ConversationPage({required this.items, required this.nextCursor});

  factory ConversationPage.fromJson(Map<String, dynamic> json) {
    final raw = json['items'];
    return ConversationPage(
      items: raw is List
          ? raw
                .whereType<Map<String, dynamic>>()
                .map(ConversationSummary.fromJson)
                .toList(growable: false)
          : const <ConversationSummary>[],
      nextCursor: json['nextCursor'] as String?,
    );
  }

  final List<ConversationSummary> items;
  final String? nextCursor;
}

class ChatMessage {
  const ChatMessage({
    required this.messageId,
    required this.clientMessageId,
    required this.body,
    required this.mine,
    required this.createdAt,
  });

  factory ChatMessage.fromJson(Map<String, dynamic> json) {
    return ChatMessage(
      messageId: json['messageId'] as String,
      clientMessageId: json['clientMessageId'] as String? ?? '',
      body: json['body'] as String? ?? '',
      mine: json['mine'] as bool? ?? false,
      createdAt: json['createdAt'] as String? ?? '',
    );
  }

  final String messageId;
  final String clientMessageId;
  final String body;
  final bool mine;
  final String createdAt;
}

class MessagePage {
  const MessagePage({required this.items, required this.nextCursor});

  factory MessagePage.fromJson(Map<String, dynamic> json) {
    final raw = json['items'];
    return MessagePage(
      items: raw is List
          ? raw
                .whereType<Map<String, dynamic>>()
                .map(ChatMessage.fromJson)
                .toList(growable: false)
          : const <ChatMessage>[],
      nextCursor: json['nextCursor'] as String?,
    );
  }

  final List<ChatMessage> items;
  final String? nextCursor;
}

enum NotificationKind { match, newMessage, unknown }

class AppNotification {
  const AppNotification({
    required this.notificationId,
    required this.kind,
    required this.conversationId,
    required this.read,
    required this.createdAt,
  });

  factory AppNotification.fromJson(Map<String, dynamic> json) {
    final kind = switch (json['kind']) {
      'MATCH' => NotificationKind.match,
      'NEW_MESSAGE' => NotificationKind.newMessage,
      _ => NotificationKind.unknown,
    };
    return AppNotification(
      notificationId: json['notificationId'] as String,
      kind: kind,
      conversationId: json['conversationId'] as String?,
      read: json['read'] as bool? ?? false,
      createdAt: json['createdAt'] as String? ?? '',
    );
  }

  final String notificationId;
  final NotificationKind kind;
  final String? conversationId;
  final bool read;
  final String createdAt;
}

class NotificationPage {
  const NotificationPage({
    required this.items,
    required this.nextCursor,
    required this.unreadCount,
  });

  factory NotificationPage.fromJson(Map<String, dynamic> json) {
    final raw = json['items'];
    return NotificationPage(
      items: raw is List
          ? raw
                .whereType<Map<String, dynamic>>()
                .map(AppNotification.fromJson)
                .toList(growable: false)
          : const <AppNotification>[],
      nextCursor: json['nextCursor'] as String?,
      unreadCount: (json['unreadCount'] as num?)?.toInt() ?? 0,
    );
  }

  final List<AppNotification> items;
  final String? nextCursor;
  final int unreadCount;
}

enum ReportTargetType { profile, message }

enum ReportCategory {
  harassment('HARASSMENT', 'Harassment'),
  impersonation('IMPERSONATION', 'Impersonation'),
  unwantedContent('UNWANTED_CONTENT', 'Unwanted content'),
  spam('SPAM', 'Spam'),
  other('OTHER', 'Something else');

  const ReportCategory(this.wireValue, this.label);

  final String wireValue;
  final String label;
}

/// Validates optional report details the same way the server does: trimmed and at most 500 characters.
String? validateReportDetails(String? raw) {
  final trimmed = raw?.trim() ?? '';
  if (trimmed.length > 500) {
    return 'Keep details under 500 characters.';
  }
  return null;
}

/// Client-side message validation mirroring the server rules: trimmed, 1 to 2000 code points.
String? validateMessageBody(String raw) {
  final trimmed = raw.trim();
  if (trimmed.isEmpty) {
    return 'Write a message before sending.';
  }
  if (trimmed.runes.length > maxMessageLength) {
    return 'Messages can be up to $maxMessageLength characters.';
  }
  return null;
}
