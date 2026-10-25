import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;

import '../../../core/config/app_config.dart';
import '../../../core/network/api_result.dart';
import '../../../core/network/pritimitra_api_client.dart';
import '../data/account_api.dart';
import '../data/amplify_auth_gateway.dart';
import '../data/auth_gateway.dart';
import '../domain/auth_state.dart';

final authGatewayProvider = Provider<AuthGateway>((ref) {
  return AmplifyAuthGateway(config: ref.watch(appConfigProvider));
});

final apiClientProvider = Provider<PritiMitraApiClient?>((ref) {
  final baseUri = ref.watch(appConfigProvider).apiBaseUri;
  if (baseUri == null) {
    return null;
  }
  final httpClient = http.Client();
  ref.onDispose(httpClient.close);
  return PritiMitraApiClient(
    baseUri: baseUri,
    tokens: ref.watch(authGatewayProvider),
    httpClient: httpClient,
  );
});

final accountStatusSourceProvider = Provider<AccountStatusSource>((ref) {
  return AccountApi(client: ref.watch(apiClientProvider));
});

final authControllerProvider = NotifierProvider<AuthController, AuthState>(
  AuthController.new,
);

/// Owns the session lifecycle: restore on launch, sign-in, sign-out, and the
/// backend status check that gates protected routes.
class AuthController extends Notifier<AuthState> {
  @override
  AuthState build() {
    Future<void>.microtask(_restoreSession);
    return const AuthInitializing();
  }

  AuthGateway get _gateway => ref.read(authGatewayProvider);

  Future<void> _restoreSession() async {
    try {
      final signedIn = await _gateway.restoreSession();
      if (!signedIn) {
        state = const AuthSignedOut();
        return;
      }
    } catch (_) {
      state = const AuthSignedOut();
      return;
    }
    await _loadAccountStatus();
  }

  /// Signs in and loads the backend status. Errors are rethrown for the screen.
  Future<void> signIn({required String email, required String password}) async {
    await _gateway.signIn(email: email, password: password);
    await _loadAccountStatus();
  }

  Future<void> signOut() async {
    try {
      await _gateway.signOut();
    } catch (_) {
      // Local state still moves to signed out so the user is never stuck in a session.
    }
    state = const AuthSignedOut();
  }

  /// Ends the local session after the refresh token stops working.
  Future<void> expireSession() async {
    try {
      await _gateway.signOut();
    } catch (_) {
      // Ignored: the session is already unusable.
    }
    state = const AuthSignedOut(sessionExpired: true);
  }

  /// Re-checks the backend status, for example after the user verifies their email.
  Future<void> retryAccountStatus() async {
    if (state is AuthSignedIn) {
      await _loadAccountStatus();
    }
  }

  Future<void> _loadAccountStatus() async {
    state = const AuthInitializing();
    final result = await ref.read(accountStatusSourceProvider).fetchAccountStatus();
    switch (result) {
      case ApiSuccess(:final data):
        state = AuthSignedIn(status: data);
      case ApiUnauthorized():
        await expireSession();
      case ApiForbidden() || ApiUnavailable():
        // Unknown status: keep protected routes closed and let the user retry.
        state = const AuthSignedIn();
    }
  }
}
