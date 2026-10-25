import '../../../core/network/api_result.dart';
import '../../../core/network/pritimitra_api_client.dart';
import '../../profile/data/profile_api.dart';
import '../domain/discovery_models.dart';

/// Typed access to `/api/v1/discovery` and `/api/v1/likes`. The server decides who is discoverable and who may be liked.
class DiscoveryApi {
  DiscoveryApi({required this.client});

  final PritiMitraApiClient? client;

  /// Fetches one page. Pass the previous [DiscoveryPage.nextCursor] to continue.
  Future<ApiResult<DiscoveryPage>> fetchPage({String? cursor, int size = 20}) {
    final query = StringBuffer('/api/v1/discovery?size=$size');
    if (cursor != null) {
      query.write('&cursor=${Uri.encodeQueryComponent(cursor)}');
    }
    return _run(
      (client) => client.getJson(query.toString()),
      DiscoveryPage.fromJson,
    );
  }

  /// Likes a profile. Repeating the call is safe. Likes never create matches on their own.
  Future<ApiResult<bool>> like(String publicId) {
    return _run(
      (client) => client.putJson(
        '/api/v1/likes/${Uri.encodeComponent(publicId)}',
        const {},
      ),
      (body) => body['liked'] as bool,
    );
  }

  /// Removes a like. Repeating the call is safe.
  Future<ApiResult<bool>> unlike(String publicId) {
    return _run(
      (client) =>
          client.deleteJson('/api/v1/likes/${Uri.encodeComponent(publicId)}'),
      (body) => body['liked'] as bool,
    );
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
