import 'dart:async';
import 'dart:convert';

import 'package:amplify_auth_cognito/amplify_auth_cognito.dart';
import 'package:amplify_flutter/amplify_flutter.dart';

import '../../../core/config/app_config.dart';
import '../domain/auth_error.dart';
import 'auth_gateway.dart';

/// Builds the Amplify Gen2 `amplify_outputs` shape from `--dart-define` values.
/// Nothing is read from disk, so no generated config file needs to be committed.
Map<String, dynamic> buildAmplifyOutputs(AppConfig config) {
  return {
    'version': '1',
    'auth': {
      'aws_region': config.cognitoRegion.trim(),
      'user_pool_id': config.cognitoUserPoolId.trim(),
      'user_pool_client_id': config.cognitoClientId.trim(),
      'username_attributes': ['email'],
      'standard_required_attributes': ['email'],
      'user_verification_types': ['email'],
      'unauthenticated_identities_enabled': false,
    },
  };
}

/// Amplify-backed [AuthGateway]. Amplify stores the refresh token in platform
/// secure storage on native targets. Access tokens are only returned to callers.
class AmplifyAuthGateway implements AuthGateway {
  AmplifyAuthGateway({required this.config});

  final AppConfig config;
  Future<void>? _configuring;

  Future<void> _ensureConfigured() {
    if (!config.isAuthConfigured) {
      throw const AuthOperationException(AuthErrorKind.notConfigured);
    }
    return _configuring ??= _configure().catchError((Object error) {
      _configuring = null;
      throw mapAmplifyAuthError(error);
    });
  }

  Future<void> _configure() async {
    if (Amplify.isConfigured) {
      return;
    }
    await Amplify.addPlugin(AmplifyAuthCognito());
    await Amplify.configure(jsonEncode(buildAmplifyOutputs(config)));
  }

  Future<T> _run<T>(Future<T> Function() operation) async {
    await _ensureConfigured();
    try {
      return await operation();
    } on AuthOperationException {
      rethrow;
    } catch (error) {
      throw mapAmplifyAuthError(error);
    }
  }

  @override
  Future<bool> restoreSession() async {
    try {
      return await _run(() async => (await _fetchSession()).isSignedIn);
    } on AuthOperationException catch (error) {
      if (error.kind == AuthErrorKind.notConfigured) {
        return false;
      }
      rethrow;
    }
  }

  @override
  Future<String?> accessToken({bool forceRefresh = false}) async {
    if (!config.isAuthConfigured) {
      return null;
    }
    await _ensureConfigured();
    try {
      final session = await _fetchSession(forceRefresh: forceRefresh);
      if (!session.isSignedIn) {
        return null;
      }
      return session.userPoolTokensResult.valueOrNull?.accessToken.raw;
    } on SignedOutException {
      return null;
    } on SessionExpiredException {
      return null;
    }
  }

  Future<CognitoAuthSession> _fetchSession({bool forceRefresh = false}) async {
    final session = await Amplify.Auth.fetchAuthSession(
      options: FetchAuthSessionOptions(forceRefresh: forceRefresh),
    );
    return session as CognitoAuthSession;
  }

  @override
  Future<void> signUp({required String email, required String password}) {
    return _run(() async {
      await Amplify.Auth.signUp(
        username: email,
        password: password,
        options: SignUpOptions(
          userAttributes: {AuthUserAttributeKey.email: email},
        ),
      );
    });
  }

  @override
  Future<void> confirmSignUp({required String email, required String code}) {
    return _run(() async {
      await Amplify.Auth.confirmSignUp(username: email, confirmationCode: code);
    });
  }

  @override
  Future<void> resendSignUpCode({required String email}) {
    return _run(() async {
      await Amplify.Auth.resendSignUpCode(username: email);
    });
  }

  @override
  Future<void> signIn({required String email, required String password}) {
    return _run(() async {
      final result = await Amplify.Auth.signIn(
        username: email,
        password: password,
      );
      if (!result.isSignedIn) {
        throw const AuthOperationException(AuthErrorKind.unknown);
      }
    });
  }

  @override
  Future<void> signOut() {
    return _run(() async {
      await Amplify.Auth.signOut();
    });
  }

  @override
  Future<void> resetPassword({required String email}) {
    return _run(() async {
      await Amplify.Auth.resetPassword(username: email);
    });
  }

  @override
  Future<void> confirmResetPassword({
    required String email,
    required String code,
    required String newPassword,
  }) {
    return _run(() async {
      await Amplify.Auth.confirmResetPassword(
        username: email,
        newPassword: newPassword,
        confirmationCode: code,
      );
    });
  }
}

/// Maps provider exceptions to [AuthOperationException]. Account-existence
/// hints such as user-not-found are folded into generic kinds.
AuthOperationException mapAmplifyAuthError(Object error) {
  if (error is AuthOperationException) {
    return error;
  }
  if (error is NetworkException) {
    return const AuthOperationException(AuthErrorKind.network);
  }
  if (error is NotAuthorizedServiceException ||
      error is UserNotFoundException) {
    return const AuthOperationException(AuthErrorKind.invalidCredentials);
  }
  if (error is UserNotConfirmedException) {
    return const AuthOperationException(AuthErrorKind.userNotConfirmed);
  }
  if (error is UsernameExistsException || error is AliasExistsException) {
    return const AuthOperationException(AuthErrorKind.usernameExists);
  }
  if (error is InvalidPasswordException) {
    return const AuthOperationException(AuthErrorKind.invalidPassword);
  }
  if (error is CodeMismatchException) {
    return const AuthOperationException(AuthErrorKind.invalidCode);
  }
  if (error is ExpiredCodeException) {
    return const AuthOperationException(AuthErrorKind.expiredCode);
  }
  if (error is LimitExceededException ||
      error is TooManyRequestsException ||
      error is TooManyFailedAttemptsException) {
    return const AuthOperationException(AuthErrorKind.limitExceeded);
  }
  if (error is InvalidParameterException || error is AuthValidationException) {
    return const AuthOperationException(AuthErrorKind.invalidInput);
  }
  return const AuthOperationException(AuthErrorKind.unknown);
}
