import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pritimitra/core/network/api_result.dart';
import 'package:pritimitra/core/network/pritimitra_api_client.dart';
import 'package:pritimitra/features/auth/data/auth_gateway.dart';
import 'package:pritimitra/features/messaging/data/messaging_api.dart';
import 'package:pritimitra/features/messaging/domain/messaging_models.dart';

class _FixedToken implements AccessTokenSource {
  @override
  Future<String?> accessToken({bool forceRefresh = false}) async => 'token';
}

MessagingApi _api(MockClientHandler handler) {
  final client = PritiMitraApiClient(
    baseUri: Uri.parse('https://api.example.test'),
    tokens: _FixedToken(),
    httpClient: MockClient(handler),
  );
  return MessagingApi(client: client);
}

http.Response _json(Object body, [int status = 200]) => http.Response(
  jsonEncode(body),
  status,
  headers: {'content-type': 'application/json'},
);

final _uuidPattern = RegExp(
  r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
);

void main() {
  test('parses matches and keeps the cursor', () async {
    final api = _api((request) async {
      expect(request.url.path, '/api/v1/matches');
      expect(request.url.queryParameters['size'], '20');
      return _json({
        'items': [
          {
            'matchPublicId': 'm-1',
            'status': 'ACTIVE',
            'displayName': 'Asha',
            'available': true,
            'matchedAt': '2025-01-01T00:00:00Z',
          },
        ],
        'nextCursor': 'abc',
      });
    });

    final result = await api.fetchMatches();
    expect(result, isA<ApiSuccess<MatchPage>>());
    final page = (result as ApiSuccess<MatchPage>).data;
    expect(page.items.single.matchPublicId, 'm-1');
    expect(page.items.single.available, isTrue);
    expect(page.nextCursor, 'abc');
  });

  test('unmatch accepts a 204 response', () async {
    final api = _api((request) async {
      expect(request.method, 'DELETE');
      expect(request.url.path, '/api/v1/matches/m-1');
      return http.Response('', 204);
    });

    expect(await api.unmatch('m-1'), isA<ApiSuccess<void>>());
  });

  test('send posts the client message id and body', () async {
    final api = _api((request) async {
      expect(request.method, 'POST');
      expect(request.url.path, '/api/v1/conversations/c-1/messages');
      final sent = jsonDecode(request.body) as Map<String, dynamic>;
      expect(sent['clientMessageId'], 'id-1');
      expect(sent['body'], 'Hello');
      return _json({
        'messageId': 'msg-1',
        'clientMessageId': 'id-1',
        'body': 'Hello',
        'mine': true,
        'createdAt': '2025-01-01T00:00:00Z',
      }, 201);
    });

    final result = await api.sendMessage(
      conversationId: 'c-1',
      clientMessageId: 'id-1',
      body: 'Hello',
    );
    final message = (result as ApiSuccess<ChatMessage>).data;
    expect(message.messageId, 'msg-1');
    expect(message.mine, isTrue);
  });

  test(
    'conversation-closed rejection is surfaced as a typed conflict',
    () async {
      final api = _api(
        (request) async => _json({'code': 'CONVERSATION_CLOSED'}, 409),
      );

      final result = await api.sendMessage(
        conversationId: 'c-1',
        clientMessageId: 'id-1',
        body: 'Hi',
      );
      expect(result, isA<ApiUnavailable<ChatMessage>>());
      expect(
        (result as ApiUnavailable<ChatMessage>).code,
        'CONVERSATION_CLOSED',
      );
    },
  );

  test('notification list parses kinds and unread count', () async {
    final api = _api(
      (request) async => _json({
        'items': [
          {
            'notificationId': 'n-1',
            'kind': 'NEW_MESSAGE',
            'conversationId': 'c-1',
            'read': false,
            'createdAt': '2025-01-01T00:00:00Z',
          },
          {
            'notificationId': 'n-2',
            'kind': 'MATCH',
            'conversationId': 'c-1',
            'read': true,
            'createdAt': '2025-01-01T00:00:00Z',
          },
        ],
        'nextCursor': null,
        'unreadCount': 1,
      }),
    );

    final page =
        (await api.fetchNotifications() as ApiSuccess<NotificationPage>).data;
    expect(page.unreadCount, 1);
    expect(page.items.first.kind, NotificationKind.newMessage);
    expect(page.items.last.kind, NotificationKind.match);
  });

  test('mark read uses the no-content path', () async {
    final api = _api((request) async {
      expect(request.method, 'PUT');
      expect(request.url.path, '/api/v1/notifications/n-1/read');
      return http.Response('', 204);
    });

    expect(await api.markNotificationRead('n-1'), isA<ApiSuccess<void>>());
  });

  test('report sends the category and omits blank details', () async {
    final api = _api((request) async {
      final sent = jsonDecode(request.body) as Map<String, dynamic>;
      expect(sent['targetType'], 'MESSAGE');
      expect(sent['category'], 'SPAM');
      expect(sent.containsKey('details'), isFalse);
      return _json({'reportId': 'r-1', 'status': 'PENDING'}, 201);
    });

    final result = await api.report(
      targetType: ReportTargetType.message,
      targetId: 'msg-1',
      category: ReportCategory.spam,
      details: '   ',
    );
    expect((result as ApiSuccess<String>).data, 'r-1');
  });

  test('client message ids are version 4 UUIDs and differ per call', () {
    final api = MessagingApi(client: null);
    final first = api.newClientMessageId();
    final second = api.newClientMessageId();
    expect(first, matches(_uuidPattern));
    expect(second, isNot(first));
  });

  test('message validation trims and enforces the 2000 character limit', () {
    expect(validateMessageBody('   '), isNotNull);
    expect(validateMessageBody('Hi'), isNull);
    expect(validateMessageBody('a' * maxMessageLength), isNull);
    expect(validateMessageBody('a' * (maxMessageLength + 1)), isNotNull);
    expect(validateReportDetails('x' * 501), isNotNull);
    expect(validateReportDetails('x' * 500), isNull);
  });

  test('without a client the API reports unavailable', () async {
    final api = MessagingApi(client: null);
    expect(await api.fetchMatches(), isA<ApiUnavailable<MatchPage>>());
  });
}
