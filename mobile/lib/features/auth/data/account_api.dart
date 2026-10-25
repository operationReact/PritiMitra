import '../../../core/network/api_result.dart';
import '../../../core/network/pritimitra_api_client.dart';
import '../domain/account_status.dart';
import 'auth_gateway.dart';

/// Reads `GET /api/v1/me`. Without a configured API client it reports unavailable.
class AccountApi implements AccountStatusSource {
  AccountApi({required this.client});

  final PritiMitraApiClient? client;

  @override
  Future<ApiResult<AccountStatus>> fetchAccountStatus() async {
    final client = this.client;
    if (client == null) {
      return const ApiUnavailable();
    }
    final result = await client.getJson('/api/v1/me');
    return switch (result) {
      ApiSuccess(:final data) => _statusFromBody(data),
      ApiForbidden(:final code) => _statusFromForbidden(code),
      ApiUnauthorized(:final code) => ApiUnauthorized(code: code),
      ApiUnavailable(:final statusCode) => ApiUnavailable(statusCode: statusCode),
    };
  }

  ApiResult<AccountStatus> _statusFromBody(Map<String, dynamic> body) {
    final status = AccountStatus.fromWire(body['status'] as String?);
    if (status == null) {
      return const ApiUnavailable();
    }
    return ApiSuccess(status);
  }

  ApiResult<AccountStatus> _statusFromForbidden(String? code) {
    final status = accountStatusForForbiddenCode(code);
    if (status == null) {
      return ApiForbidden(code: code);
    }
    return ApiSuccess(status);
  }
}
