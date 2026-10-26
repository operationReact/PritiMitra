import 'dart:math';

import '../../../core/network/api_result.dart';
import '../../../core/network/pritimitra_api_client.dart';
import '../../profile/data/profile_api.dart';
import '../domain/messaging_models.dart';

/// Typed access to matches, conversations, notifications, blocks and reports. The server decides every outcome.
class MessagingApi {
  MessagingApi({required this.client, Random? random})
    : _random = random ?? Random.secure();

  final PritiMitraApiClient? client;
  final Random _random;

  /// A fresh RFC 4122 version 4 UUID. Reuse the same value when retrying the same send so the server stores it once.
  String newClientMessageId() {
    final bytes = List<int>.generate(16, (_) => _random.nextInt(256));
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-'
        '${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
  }

  Future<ApiResult<MatchPage>> fetchMatches({String? cursor, int size = 20}) {
    return _run(
      (client) => client.getJson(_page('/api/v1/matches', cursor, size)),
      MatchPage.fromJson,
    );
  }

  Future<ApiResult<void>> unmatch(String matchPublicId) async {
    final client = this.client;
    if (client == null) {
      return const ApiUnavailable();
    }
    return client.deleteNoContent(
      '/api/v1/matches/${Uri.encodeComponent(matchPublicId)}',
    );
  }

  Future<ApiResult<ConversationPage>> fetchConversations({
    String? cursor,
    int size = 20,
  }) {
    return _run(
      (client) => client.getJson(_page('/api/v1/conversations', cursor, size)),
      ConversationPage.fromJson,
    );
  }

  /// Fetches one page of history, newest first. Pass the previous [MessagePage.nextCursor] to go further back.
  Future<ApiResult<MessagePage>> fetchMessages(
    String conversationId, {
    String? cursor,
    int size = 30,
  }) {
    final path = _page(
      '/api/v1/conversations/${Uri.encodeComponent(conversationId)}/messages',
      cursor,
      size,
    );
    return _run((client) => client.getJson(path), MessagePage.fromJson);
  }

  /// Sends a text message. Retries must reuse [clientMessageId]; the server returns the stored message for a replay.
  Future<ApiResult<ChatMessage>> sendMessage({
    required String conversationId,
    required String clientMessageId,
    required String body,
  }) {
    return _run(
      (client) => client.postJson(
        '/api/v1/conversations/${Uri.encodeComponent(conversationId)}/messages',
        body: {'clientMessageId': clientMessageId, 'body': body},
      ),
      ChatMessage.fromJson,
    );
  }

  /// Marks messages up to [messageId] as read. The server never moves the cursor backwards.
  Future<ApiResult<int>> markRead({
    required String conversationId,
    required String messageId,
  }) {
    return _run(
      (client) => client.putJson(
        '/api/v1/conversations/${Uri.encodeComponent(conversationId)}/read',
        {'messageId': messageId},
      ),
      (body) => (body['unreadCount'] as num?)?.toInt() ?? 0,
    );
  }

  Future<ApiResult<NotificationPage>> fetchNotifications({
    String? cursor,
    int size = 20,
  }) {
    return _run(
      (client) => client.getJson(_page('/api/v1/notifications', cursor, size)),
      NotificationPage.fromJson,
    );
  }

  Future<ApiResult<void>> markNotificationRead(String notificationId) {
    final client = this.client;
    if (client == null) {
      return Future.value(const ApiUnavailable());
    }
    return client.putNoContent(
      '/api/v1/notifications/${Uri.encodeComponent(notificationId)}/read',
      const {},
    );
  }

  Future<ApiResult<void>> markAllNotificationsRead() {
    final client = this.client;
    if (client == null) {
      return Future.value(const ApiUnavailable());
    }
    return client.putNoContent('/api/v1/notifications/read-all', const {});
  }

  Future<ApiResult<void>> block(String profilePublicId) {
    final client = this.client;
    if (client == null) {
      return Future.value(const ApiUnavailable());
    }
    return client.putNoContent(
      '/api/v1/blocks/${Uri.encodeComponent(profilePublicId)}',
      const {},
    );
  }

  Future<ApiResult<void>> unblock(String profilePublicId) async {
    final client = this.client;
    if (client == null) {
      return const ApiUnavailable();
    }
    return client.deleteNoContent(
      '/api/v1/blocks/${Uri.encodeComponent(profilePublicId)}',
    );
  }

  /// Files a report. Reports are private to the platform; the server returns only a receipt.
  Future<ApiResult<String>> report({
    required ReportTargetType targetType,
    required String targetId,
    required ReportCategory category,
    String? details,
  }) {
    final trimmed = details?.trim();
    return _run(
      (client) => client.postJson(
        '/api/v1/reports',
        body: {
          'targetType': targetType == ReportTargetType.profile
              ? 'PROFILE'
              : 'MESSAGE',
          'targetId': targetId,
          'category': category.wireValue,
          if (trimmed != null && trimmed.isNotEmpty) 'details': trimmed,
        },
      ),
      (body) => body['reportId'] as String? ?? '',
    );
  }

  String _page(String base, String? cursor, int size) {
    final query = StringBuffer('$base?size=$size');
    if (cursor != null) {
      query.write('&cursor=${Uri.encodeQueryComponent(cursor)}');
    }
    return query.toString();
  }

  Future<ApiResult<T>> _run<T>(
    Future<ApiResult<Map<String, dynamic>>> Function(PritiMitraApiClient client)
    call,
    T Function(Map<String, dynamic>) parse,
  ) async {
    final client = this.client;
    if (client == null) {
      return const ApiUnavailable();
    }
    return mapApiResult(await call(client), parse);
  }
}
