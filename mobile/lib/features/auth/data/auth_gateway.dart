import '../../../core/network/api_result.dart';
import '../domain/account_status.dart';
import '../domain/auth_error.dart';

/// Supplies the current access token to the API client.
abstract interface class AccessTokenSource {
  /// Returns null when there is no signed-in session. Throws only for unexpected failures.
  Future<String?> accessToken({bool forceRefresh = false});
}

/// Cognito-backed auth operations. Every failure is an [AuthOperationException].
abstract interface class AuthGateway implements AccessTokenSource {
  /// Returns true when a valid session already exists on this device.
  Future<bool> restoreSession();

  Future<void> signUp({required String email, required String password});

  Future<void> confirmSignUp({required String email, required String code});

  Future<void> resendSignUpCode({required String email});

  Future<void> signIn({required String email, required String password});

  Future<void> signOut();

  Future<void> resetPassword({required String email});

  Future<void> confirmResetPassword({
    required String email,
    required String code,
    required String newPassword,
  });
}

/// Loads the backend account status for the signed-in user.
abstract interface class AccountStatusSource {
  /// Success carries the mapped [AccountStatus], including statuses implied by
  /// known 403 codes. Unknown 403s, 5xx, and network failures return other results.
  Future<ApiResult<AccountStatus>> fetchAccountStatus();
}
