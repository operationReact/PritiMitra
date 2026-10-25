import '../../../core/network/api_result.dart';
import '../../../core/network/pritimitra_api_client.dart';
import '../domain/own_profile.dart';

/// Typed access to the owner's profile endpoints. The server enforces ownership and account state.
class ProfileApi {
  ProfileApi({required this.client});

  final PritiMitraApiClient? client;

  Future<ApiResult<OwnProfile>> fetchOwnProfile() async {
    final client = this.client;
    if (client == null) {
      return const ApiUnavailable();
    }
    return mapApiResult(
      await client.getJson('/api/v1/profile'),
      OwnProfile.fromJson,
    );
  }

  /// Full replacement update. Discoverability is changed through [setDiscoverable] only.
  Future<ApiResult<OwnProfile>> saveProfile(OwnProfile profile) async {
    final client = this.client;
    if (client == null) {
      return const ApiUnavailable();
    }
    return mapApiResult(
      await client.putJson('/api/v1/profile', profile.toUpdateJson()),
      OwnProfile.fromJson,
    );
  }

  Future<ApiResult<OwnProfile>> setDiscoverable({
    required bool discoverable,
  }) async {
    final client = this.client;
    if (client == null) {
      return const ApiUnavailable();
    }
    return mapApiResult(
      await client.putJson('/api/v1/profile/discoverability', {
        'discoverable': discoverable,
      }),
      OwnProfile.fromJson,
    );
  }
}

/// Converts a raw client result into a typed one. A body that cannot be parsed is treated as
/// an unavailable service rather than crashing the screen.
ApiResult<T> mapApiResult<T>(
  ApiResult<Map<String, dynamic>> result,
  T Function(Map<String, dynamic> json) parse,
) {
  return switch (result) {
    ApiSuccess(:final data) => _parseOrUnavailable(data, parse),
    ApiUnauthorized(:final code) => ApiUnauthorized<T>(code: code),
    ApiForbidden(:final code) => ApiForbidden<T>(code: code),
    ApiUnavailable(:final statusCode, :final code) => ApiUnavailable<T>(
      statusCode: statusCode,
      code: code,
    ),
  };
}

ApiResult<T> _parseOrUnavailable<T>(
  Map<String, dynamic> data,
  T Function(Map<String, dynamic> json) parse,
) {
  try {
    return ApiSuccess(parse(data));
  } on TypeError {
    return const ApiUnavailable();
  }
}
