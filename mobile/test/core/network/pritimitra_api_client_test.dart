import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pritimitra/core/network/api_result.dart';
import 'package:pritimitra/core/network/pritimitra_api_client.dart';
import 'package:pritimitra/features/auth/data/auth_gateway.dart';

class _FakeTokens implements AccessTokenSource {
  _FakeTokens(this.tokens);

  /// Tokens returned in order. The last value repeats.
  final List<String?> tokens;
  final List<bool> forceRefreshCalls = [];
  int _index = 0;

  @override
  Future<String?> accessToken({bool forceRefresh = false}) async {
    forceRefreshCalls.add(forceRefresh);
    final value = tokens[_index < tokens.length ? _index : tokens.length - 1];
    _index++;
    return value;
  }
}

PritiMitraApiClient _client(MockClientHandler handler, _FakeTokens tokens) {
  return PritiMitraApiClient(
    baseUri: Uri.parse('https://api.example.test'),
    tokens: tokens,
    httpClient: MockClient(handler),
  );
}

void main() {
  test('sends the access token as a Bearer header to the API base URL', () async {
    late http.Request captured;
    final client = _client((request) async {
      captured = request;
      return http.Response(jsonEncode({'id': 'u1', 'status': 'ACTIVE'}), 200);
    }, _FakeTokens(['access-1']));

    final result = await client.getJson('/api/v1/me');

    expect(result, isA<ApiSuccess<Map<String, dynamic>>>());
    expect(captured.url.toString(), 'https://api.example.test/api/v1/me');
    expect(captured.headers['Authorization'], 'Bearer access-1');
  });

  test('without a session it reports unauthorized and makes no request', () async {
    var called = false;
    final client = _client((request) async {
      called = true;
      return http.Response('{}', 200);
    }, _FakeTokens([null]));

    final result = await client.getJson('/api/v1/me');

    expect(result, isA<ApiUnauthorized<Map<String, dynamic>>>());
    expect(called, isFalse);
  });

  test('401 triggers one forced refresh and a retry', () async {
    final seenAuth = <String?>[];
    final tokens = _FakeTokens(['stale', 'fresh']);
    final client = _client((request) async {
      seenAuth.add(request.headers['Authorization']);
      if (request.headers['Authorization'] == 'Bearer stale') {
        return http.Response('{"code":"TOKEN_EXPIRED"}', 401);
      }
      return http.Response('{"status":"ACTIVE"}', 200);
    }, tokens);

    final result = await client.getJson('/api/v1/me');

    expect(result, isA<ApiSuccess<Map<String, dynamic>>>());
    expect(seenAuth, ['Bearer stale', 'Bearer fresh']);
    expect(tokens.forceRefreshCalls, [false, true]);
  });

  test('401 that persists after refresh is unauthorized', () async {
    final client = _client(
      (request) async => http.Response('{"code":"UNAUTHORIZED"}', 401),
      _FakeTokens(['a', 'b']),
    );

    final result = await client.getJson('/api/v1/me');

    expect(result, isA<ApiUnauthorized<Map<String, dynamic>>>());
    expect((result as ApiUnauthorized).code, 'UNAUTHORIZED');
  });

  test('403 surfaces the business code', () async {
    final client = _client(
      (request) async => http.Response(
        '{"status":403,"code":"AGE_INELIGIBLE"}',
        403,
      ),
      _FakeTokens(['a']),
    );

    final result = await client.getJson('/api/v1/me');

    expect(result, isA<ApiForbidden<Map<String, dynamic>>>());
    expect((result as ApiForbidden).code, 'AGE_INELIGIBLE');
  });

  test('5xx, malformed JSON and network failures are unavailable', () async {
    final serverError = _client(
      (request) async => http.Response('boom', 503),
      _FakeTokens(['a']),
    );
    final malformed = _client(
      (request) async => http.Response('not json', 200),
      _FakeTokens(['a']),
    );
    final network = _client(
      (request) async => throw http.ClientException('offline'),
      _FakeTokens(['a']),
    );

    final serverResult = await serverError.getJson('/api/v1/me');
    expect(serverResult, isA<ApiUnavailable<Map<String, dynamic>>>());
    expect((serverResult as ApiUnavailable).statusCode, 503);
    expect(
      await malformed.getJson('/api/v1/me'),
      isA<ApiUnavailable<Map<String, dynamic>>>(),
    );
    expect(
      await network.getJson('/api/v1/me'),
      isA<ApiUnavailable<Map<String, dynamic>>>(),
    );
  });
}
