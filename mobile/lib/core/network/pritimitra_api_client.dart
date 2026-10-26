import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';

import '../../features/auth/data/auth_gateway.dart';
import 'api_result.dart';
import 'progress_multipart_request.dart';

/// Minimal JSON client for the PritiMitra backend. It sends the Cognito access
/// token as a Bearer header and never logs or persists it.
class PritiMitraApiClient {
  PritiMitraApiClient({
    required this.baseUri,
    required this.tokens,
    required this.httpClient,
    this.timeout = const Duration(seconds: 15),
  });

  /// Photo uploads can legitimately take longer than JSON calls on mobile networks.
  static const uploadTimeout = Duration(seconds: 60);

  final Uri baseUri;
  final AccessTokenSource tokens;
  final http.Client httpClient;
  final Duration timeout;

  Future<ApiResult<Map<String, dynamic>>> getJson(String path) {
    return _requestJson('GET', path);
  }

  Future<ApiResult<Map<String, dynamic>>> putJson(
    String path,
    Map<String, dynamic> body,
  ) {
    return _requestJson('PUT', path, body: body);
  }

  Future<ApiResult<Map<String, dynamic>>> postJson(
    String path, {
    Map<String, dynamic>? body,
  }) {
    return _requestJson('POST', path, body: body);
  }

  /// For PUT endpoints that answer 204 with no body.
  Future<ApiResult<void>> putNoContent(
    String path,
    Map<String, dynamic> body,
  ) async {
    final response = await _requestRaw('PUT', path, body: body);
    if (response == null) {
      return const ApiUnavailable();
    }
    final failure = _failure<void>(response);
    if (failure != null) {
      return failure;
    }
    return const ApiSuccess(null);
  }

  Future<ApiResult<Map<String, dynamic>>> deleteJson(String path) {
    return _requestJson('DELETE', path);
  }

  Future<ApiResult<List<dynamic>>> getJsonList(String path) async {
    final response = await _requestRaw('GET', path);
    if (response == null) {
      return const ApiUnavailable();
    }
    final failure = _failure<List<dynamic>>(response);
    if (failure != null) {
      return failure;
    }
    try {
      final decoded = jsonDecode(response.body);
      if (decoded is List<dynamic>) {
        return ApiSuccess(decoded);
      }
    } on FormatException {
      // Fall through to the unavailable result below.
    }
    return ApiUnavailable(statusCode: response.statusCode);
  }

  Future<ApiResult<Uint8List>> getBytes(String path) async {
    final uri = baseUri.resolve(path);
    final response = await _sendAuthorized(
      uri,
      (accessToken) =>
          http.Request('GET', uri)
            ..headers['Authorization'] = 'Bearer $accessToken',
    );
    if (response == null) {
      return const ApiUnavailable();
    }
    final failure = _failure<Uint8List>(response);
    if (failure != null) {
      return failure;
    }
    return ApiSuccess(response.bodyBytes);
  }

  Future<ApiResult<void>> deleteNoContent(String path) async {
    final response = await _requestRaw('DELETE', path);
    if (response == null) {
      return const ApiUnavailable();
    }
    final failure = _failure<void>(response);
    if (failure != null) {
      return failure;
    }
    return const ApiSuccess(null);
  }

  /// Uploads one file as multipart form data. [onProgress] reports bytes
  /// handed to the transport; only the returned result confirms storage.
  Future<ApiResult<Map<String, dynamic>>> uploadFile(
    String path, {
    required String fieldName,
    required Uint8List bytes,
    required String filename,
    required MediaType contentType,
    void Function(int sentBytes, int totalBytes)? onProgress,
  }) async {
    final uri = baseUri.resolve(path);
    final response = await _sendAuthorized(
      uri,
      (accessToken) =>
          ProgressMultipartRequest('POST', uri, onProgress: onProgress)
            ..headers['Accept'] = 'application/json'
            ..headers['Authorization'] = 'Bearer $accessToken'
            ..files.add(
              http.MultipartFile.fromBytes(
                fieldName,
                bytes,
                filename: filename,
                contentType: contentType,
              ),
            ),
      timeout: uploadTimeout,
    );
    if (response == null) {
      return const ApiUnavailable();
    }
    return _interpret(response);
  }

  Future<ApiResult<Map<String, dynamic>>> _requestJson(
    String method,
    String path, {
    Map<String, dynamic>? body,
  }) async {
    final response = await _requestRaw(method, path, body: body);
    if (response == null) {
      return const ApiUnavailable();
    }
    return _interpret(response);
  }

  Future<http.Response?> _requestRaw(
    String method,
    String path, {
    Map<String, dynamic>? body,
  }) {
    final uri = baseUri.resolve(path);
    return _sendAuthorized(
      uri,
      (accessToken) => _jsonRequest(method, uri, accessToken, body),
    );
  }

  /// Sends one authorized request, retrying once after a forced token refresh
  /// on 401. Returns null on network failure or timeout. Token failures are
  /// returned as a synthetic 401 so callers map them like server rejections.
  Future<http.Response?> _sendAuthorized(
    Uri uri,
    http.BaseRequest Function(String accessToken) buildRequest, {
    Duration? timeout,
  }) async {
    final limit = timeout ?? this.timeout;
    final firstToken = await _currentToken(forceRefresh: false);
    if (firstToken == null) {
      return _noSessionResponse();
    }

    final response = await _sendOnce(buildRequest(firstToken), limit);
    if (response == null || response.statusCode != 401) {
      return response;
    }

    final refreshedToken = await _currentToken(forceRefresh: true);
    if (refreshedToken == null) {
      return _noSessionResponse();
    }
    return _sendOnce(buildRequest(refreshedToken), limit);
  }

  Future<String?> _currentToken({required bool forceRefresh}) async {
    try {
      return await tokens.accessToken(forceRefresh: forceRefresh);
    } catch (_) {
      // Token retrieval failures (not configured, refresh failed) mean no usable session.
      return null;
    }
  }

  http.Response _noSessionResponse() => http.Response('', 401);

  Future<http.Response?> _sendOnce(
    http.BaseRequest request,
    Duration limit,
  ) async {
    try {
      final streamed = await httpClient.send(request).timeout(limit);
      return await http.Response.fromStream(streamed).timeout(limit);
    } on TimeoutException {
      return null;
    } on http.ClientException {
      return null;
    }
  }

  http.Request _jsonRequest(
    String method,
    Uri uri,
    String accessToken,
    Map<String, dynamic>? body,
  ) {
    final request = http.Request(method, uri)
      ..headers.addAll({
        'Accept': 'application/json',
        'Authorization': 'Bearer $accessToken',
        if (body != null) 'Content-Type': 'application/json',
      });
    if (body != null) {
      request.body = jsonEncode(body);
    }
    return request;
  }

  /// Maps non-2xx responses to a typed failure. Returns null for 2xx.
  ApiResult<T>? _failure<T>(http.Response response) {
    final status = response.statusCode;
    if (status == 401) {
      return ApiUnauthorized(code: _errorCode(_bodyText(response)));
    }
    if (status == 403) {
      return ApiForbidden(code: _errorCode(_bodyText(response)));
    }
    if (status < 200 || status > 299) {
      return ApiUnavailable(
        statusCode: status,
        code: _errorCode(_bodyText(response)),
      );
    }
    return null;
  }

  ApiResult<Map<String, dynamic>> _interpret(http.Response response) {
    final failure = _failure<Map<String, dynamic>>(response);
    if (failure != null) {
      return failure;
    }
    try {
      final decoded = jsonDecode(response.body);
      if (decoded is Map<String, dynamic>) {
        return ApiSuccess(decoded);
      }
    } on FormatException {
      // Fall through to the unavailable result below.
    }
    return ApiUnavailable(statusCode: response.statusCode);
  }

  String _bodyText(http.Response response) {
    try {
      return response.body;
    } on FormatException {
      return '';
    }
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
