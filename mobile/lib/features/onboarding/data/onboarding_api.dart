import '../../../core/network/api_result.dart';
import '../../../core/network/pritimitra_api_client.dart';
import '../../auth/domain/account_status.dart';
import '../domain/onboarding_state.dart';
import '../domain/profile_draft.dart';

/// Typed access to `/api/v1/onboarding/*`. The server enforces every rule. The client only reports results.
class OnboardingApi {
  OnboardingApi({required this.client});

  final PritiMitraApiClient? client;

  Future<ApiResult<OnboardingState>> fetchState() {
    return _run('/api/v1/onboarding', 'GET', (body) => OnboardingState.fromJson(body));
  }

  /// Sends the date of birth in ISO-8601 format (`YYYY-MM-DD`). The server decides eligibility.
  Future<ApiResult<OnboardingState>> submitDateOfBirth(DateTime dateOfBirth) {
    final day = _isoDate(dateOfBirth);
    return _run(
      '/api/v1/onboarding/age-verification',
      'PUT',
      (body) => OnboardingState.fromJson(body),
      body: {'dateOfBirth': day},
    );
  }

  /// Records consent to the given policy versions. Both types are required for activation.
  Future<ApiResult<OnboardingState>> acceptConsents({
    required String termsVersion,
    required String privacyVersion,
  }) {
    return _run(
      '/api/v1/onboarding/consents',
      'PUT',
      (body) => OnboardingState.fromJson(body),
      body: {
        'consents': [
          {'type': 'TERMS_OF_SERVICE', 'policyVersion': termsVersion, 'accepted': true},
          {'type': 'PRIVACY_NOTICE', 'policyVersion': privacyVersion, 'accepted': true},
        ],
      },
    );
  }

  /// Reads the saved profile so a resumed session can send a complete replacement without wiping earlier input.
  Future<ApiResult<ProfileDraft>> fetchProfile() {
    return _run('/api/v1/onboarding/profile', 'GET', (body) => ProfileDraft.fromJson(body));
  }

  /// Saves the full profile. The backend treats the update as a full replacement, so callers must pass every field.
  /// Visibility flags are sent as false to keep profiles private until the user opts in.
  Future<ApiResult<OnboardingState>> saveProfile(ProfileDraft draft) {
    return _run(
      '/api/v1/onboarding/profile',
      'PUT',
      (body) => OnboardingState.fromJson(body),
      body: draft.toJson(),
    );
  }

  /// Activates the account. The server returns `PROFILE_INCOMPLETE` when a required field is missing.
  Future<ApiResult<AccountStatus>> complete() async {
    final client = this.client;
    if (client == null) {
      return const ApiUnavailable();
    }
    final result = await client.postJson('/api/v1/onboarding/complete');
    return switch (result) {
      ApiSuccess(:final data) => _statusOrUnavailable(data),
      ApiForbidden(:final code) => ApiForbidden(code: code),
      ApiUnauthorized(:final code) => ApiUnauthorized(code: code),
      ApiUnavailable(:final statusCode) => ApiUnavailable(statusCode: statusCode),
    };
  }

  Future<ApiResult<T>> _run<T>(
    String path,
    String method,
    T? Function(Map<String, dynamic>) parse, {
    Map<String, dynamic>? body,
  }) async {
    final client = this.client;
    if (client == null) {
      return const ApiUnavailable();
    }
    final result = method == 'GET'
        ? await client.getJson(path)
        : await client.putJson(path, body ?? const {});
    return switch (result) {
      ApiSuccess(:final data) => _parsedOrUnavailable(parse(data)),
      ApiForbidden(:final code) => ApiForbidden(code: code),
      ApiUnauthorized(:final code) => ApiUnauthorized(code: code),
      ApiUnavailable(:final statusCode) => ApiUnavailable(statusCode: statusCode),
    };
  }

  ApiResult<T> _parsedOrUnavailable<T>(T? value) {
    if (value == null) {
      return const ApiUnavailable();
    }
    return ApiSuccess(value);
  }

  ApiResult<AccountStatus> _statusOrUnavailable(Map<String, dynamic> body) {
    final status = AccountStatus.fromWire(body['status'] as String?);
    if (status == null) {
      return const ApiUnavailable();
    }
    return ApiSuccess(status);
  }

  static String _isoDate(DateTime value) {
    final month = value.month.toString().padLeft(2, '0');
    final day = value.day.toString().padLeft(2, '0');
    return '${value.year.toString().padLeft(4, '0')}-$month-$day';
  }
}
