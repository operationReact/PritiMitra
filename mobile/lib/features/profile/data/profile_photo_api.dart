import 'dart:typed_data';

import 'package:http_parser/http_parser.dart';

import '../../../core/network/api_result.dart';
import '../../../core/network/pritimitra_api_client.dart';
import '../domain/profile_photo.dart';

/// Thin wrapper over the owner-only profile photo endpoints.
class ProfilePhotoApi {
  const ProfilePhotoApi(this._client);

  final PritiMitraApiClient? _client;

  static const _basePath = '/api/v1/profile/photos';

  Future<ApiResult<List<ProfilePhoto>>> listPhotos() async {
    final client = _client;
    if (client == null) {
      return const ApiUnavailable();
    }
    final result = await client.getJsonList(_basePath);
    return switch (result) {
      ApiSuccess(:final data) => _parseList(data),
      ApiUnauthorized(:final code) => ApiUnauthorized(code: code),
      ApiForbidden(:final code) => ApiForbidden(code: code),
      ApiUnavailable(:final statusCode, :final code) => ApiUnavailable(
        statusCode: statusCode,
        code: code,
      ),
    };
  }

  /// Returns the image bytes for an owned photo. The bytes are only held in memory.
  Future<ApiResult<Uint8List>> photoContent(String id) async {
    final client = _client;
    if (client == null) {
      return const ApiUnavailable();
    }
    return client.getBytes('$_basePath/$id/content');
  }

  Future<ApiResult<ProfilePhoto>> uploadPhoto({
    required Uint8List bytes,
    required String filename,
    required MediaType contentType,
    void Function(int sentBytes, int totalBytes)? onProgress,
  }) async {
    final client = _client;
    if (client == null) {
      return const ApiUnavailable();
    }
    final result = await client.uploadFile(
      _basePath,
      fieldName: 'file',
      bytes: bytes,
      filename: filename,
      contentType: contentType,
      onProgress: onProgress,
    );
    return switch (result) {
      ApiSuccess(:final data) => _parsePhoto(data),
      ApiUnauthorized(:final code) => ApiUnauthorized(code: code),
      ApiForbidden(:final code) => ApiForbidden(code: code),
      ApiUnavailable(:final statusCode, :final code) => ApiUnavailable(
        statusCode: statusCode,
        code: code,
      ),
    };
  }

  /// A 404 for an already-removed photo is reported as a success so retries converge.
  Future<ApiResult<void>> deletePhoto(String id) async {
    final client = _client;
    if (client == null) {
      return const ApiUnavailable();
    }
    final result = await client.deleteNoContent('$_basePath/$id');
    if (result case ApiUnavailable(statusCode: 404, code: 'PHOTO_NOT_FOUND')) {
      return const ApiSuccess(null);
    }
    return result;
  }

  ApiResult<List<ProfilePhoto>> _parseList(List<dynamic> values) {
    try {
      return ApiSuccess(
        values
            .map((item) => ProfilePhoto.fromJson(item as Map<String, dynamic>))
            .toList(growable: false),
      );
    } on TypeError {
      return const ApiUnavailable();
    }
  }

  ApiResult<ProfilePhoto> _parsePhoto(Map<String, dynamic> value) {
    try {
      return ApiSuccess(ProfilePhoto.fromJson(value));
    } on TypeError {
      return const ApiUnavailable();
    }
  }
}
