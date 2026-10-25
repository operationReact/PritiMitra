import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

import '../../features/auth/data/auth_gateway.dart';
import 'api_result.dart';

/// Minimal JSON client for the PritiMitra backend. It sends the Cognito access
/// token as a Bearer header and never logs or persists it.
class PritiMitraApiClient {
  PritiMitraApiClient({
    required this.baseUri,
    required this.tokens,
    required this.httpClient,
    this.timeout = const Duration(seconds: 15),
  });

  final Uri baseUri;
  final AccessTokenSource tokens;
  final http.Client httpClient;
  final Duration timeout;

  Future<ApiResult<Map<String, dynamic>>> getJson(String path) {
    return _requestJson('GET', path);
  }

  Future<ApiResult<Map<String, dynamic>>> putJson(String path, Map<String, dynamic> body) {
    return _requestJson('PUT', path, body: body);
  }

  Future<ApiResult<Map<String, dynamic>>> postJson(String path) {
    return _requestJson('POST', path);
  }

  Future<ApiResult<Map<String, dynamic>>> _requestJson(
    String method,
    String path, {
    Map<String, dynamic>? body,
  }) async {
    final uri = baseUri.resolve(path);

    final String? firstToken;
    try {
      firstToken = await tokens.accessToken();
    } catch (_) {
      // Token retrieval failures (not configured, refresh failed) mean no usable session.
      return const ApiUnauthorized();
    }
    if (firstToken == null) {
      return const ApiUnauthorized();
    }

    var response = await _send(method, uri, firstToken, body);
    if (response == null) {
      return const ApiUnavailable();
    }

    if (response.statusCode == 401) {
      final String? refreshedToken;
      try {
        refreshedToken = await tokens.accessToken(forceRefresh: true);
      } catch (_) {
        return const ApiUnauthorized();
      }
      if (refreshedToken == null) {
        return const ApiUnauthorized();
      }
      response = await _send(method, uri, refreshedToken, body);
      if (response == null) {
        return const ApiUnavailable();
      }
    }

    return _interpret(response);
  }

  Future<http.Response?> _send(
    String method,
    Uri uri,
    String accessToken,
    Map<String, dynamic>? body,
  ) async {
    final headers = {
      'Accept': 'application/json',
      'Authorization': 'Bearer $accessToken',
      if (body != null) 'Content-Type': 'application/json',
    };
    try {
      final request = http.Request(method, uri)..headers.addAll(headers);
      if (body != null) {
        request.body = jsonEncode(body);
      }
      final streamed = await httpClient.send(request).timeout(timeout);
      return await http.Response.fromStream(streamed).timeout(timeout);
    } on TimeoutException {
      return null;
    } on http.ClientException {
      return null;
    }
  }

  ApiResult<Map<String, dynamic>> _interpret(http.Response response) {
    final status = response.statusCode;
    if (status == 401) {
      return ApiUnauthorized(code: _errorCode(response.body));
    }
    if (status == 403) {
      return ApiForbidden(code: _errorCode(response.body));
    }
    if (status < 200 || status > 299) {
      return ApiUnavailable(statusCode: status);
    }
    try {
      final decoded = jsonDecode(response.body);
      if (decoded is Map<String, dynamic>) {
        return ApiSuccess(decoded);
      }
    } on FormatException {
      // Fall through to the unavailable result below.
    }
    return ApiUnavailable(statusCode: status);
  }

  String? _errorCode(String body) {
    try {
      final decoded = jsonDecode(body);
      if (decoded is Map && decoded['code'] is String) {
        return decoded['code'] as String;
      }
    } on FormatException {
      return null;
    }
    return null;
  }
}
