import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pritimitra/core/network/api_result.dart';
import 'package:pritimitra/features/auth/application/auth_controller.dart';
import 'package:pritimitra/features/auth/data/auth_gateway.dart';
import 'package:pritimitra/features/auth/domain/account_status.dart';
import 'package:pritimitra/features/auth/domain/auth_error.dart';
import 'package:pritimitra/features/auth/domain/auth_state.dart';

class _FakeGateway implements AuthGateway {
  bool restoreResult = false;
  AuthOperationException? signInError;
  int signOutCalls = 0;

  @override
  Future<String?> accessToken({bool forceRefresh = false}) async => null;

  @override
  Future<bool> restoreSession() async => restoreResult;

  @override
  Future<void> signIn({required String email, required String password}) async {
    if (signInError != null) throw signInError!;
    restoreResult = true;
  }

  @override
  Future<void> signOut() async {
    signOutCalls++;
    restoreResult = false;
  }

  @override
  Future<void> signUp({required String email, required String password}) async {}

  @override
  Future<void> confirmSignUp({required String email, required String code}) async {}

  @override
  Future<void> resendSignUpCode({required String email}) async {}

  @override
  Future<void> resetPassword({required String email}) async {}

  @override
  Future<void> confirmResetPassword({
    required String email,
    required String code,
    required String newPassword,
  }) async {}
}

class _FakeAccountSource implements AccountStatusSource {
  _FakeAccountSource(this.results);

  /// Results returned in order. The last value repeats.
  final List<ApiResult<AccountStatus>> results;
  int _index = 0;

  @override
  Future<ApiResult<AccountStatus>> fetchAccountStatus() async {
    final value = results[_index < results.length ? _index : results.length - 1];
    _index++;
    return value;
  }
}

ProviderContainer _container(_FakeGateway gateway, _FakeAccountSource source) {
  final container = ProviderContainer(
    overrides: [
      authGatewayProvider.overrideWithValue(gateway),
      accountStatusSourceProvider.overrideWithValue(source),
    ],
  );
  addTearDown(container.dispose);
  return container;
}

/// Lets the microtask that restores the session finish.
Future<void> _settle() => Future<void>.delayed(Duration.zero);

void main() {
  test('no stored session ends signed out', () async {
    final container = _container(_FakeGateway(), _FakeAccountSource([]));
    container.read(authControllerProvider);
    await _settle();

    expect(container.read(authControllerProvider), isA<AuthSignedOut>());
  });

  test('restored session loads the account status', () async {
    final gateway = _FakeGateway()..restoreResult = true;
    final container = _container(
      gateway,
      _FakeAccountSource([const ApiSuccess(AccountStatus.active)]),
    );
    container.read(authControllerProvider);
    await _settle();

    expect(
      container.read(authControllerProvider),
      isA<AuthSignedIn>().having((s) => s.status, 'status', AccountStatus.active),
    );
  });

  test('a failing restore never leaves the app stuck loading', () async {
    final gateway = _ThrowingRestoreGateway();
    final container = _container(gateway, _FakeAccountSource([]));
    container.read(authControllerProvider);
    await _settle();

    expect(container.read(authControllerProvider), isA<AuthSignedOut>());
  });

  test('sign-in success loads status and grants home only when ACTIVE', () async {
    final container = _container(
      _FakeGateway(),
      _FakeAccountSource([const ApiSuccess(AccountStatus.active)]),
    );
    container.read(authControllerProvider);
    await _settle();

    await container
        .read(authControllerProvider.notifier)
        .signIn(email: 'a@example.test', password: 'pw-123456');

    expect(
      decideAccess(container.read(authControllerProvider)),
      AccessDecision.home,
    );
  });

  test('sign-in failure is rethrown and leaves state unchanged', () async {
    final gateway = _FakeGateway()
      ..signInError = const AuthOperationException(AuthErrorKind.invalidCredentials);
    final container = _container(gateway, _FakeAccountSource([]));
    container.read(authControllerProvider);
    await _settle();

    await expectLater(
      container
          .read(authControllerProvider.notifier)
          .signIn(email: 'a@example.test', password: 'wrong'),
      throwsA(
        isA<AuthOperationException>().having(
          (e) => e.kind,
          'kind',
          AuthErrorKind.invalidCredentials,
        ),
      ),
    );
    expect(container.read(authControllerProvider), isA<AuthSignedOut>());
  });

  test('INELIGIBLE status routes to the ineligible decision', () async {
    final gateway = _FakeGateway()..restoreResult = true;
    final container = _container(
      gateway,
      _FakeAccountSource([
        const ApiSuccess(AccountStatus.ineligible),
      ]),
    );
    container.read(authControllerProvider);
    await _settle();

    expect(
      decideAccess(container.read(authControllerProvider)),
      AccessDecision.ineligible,
    );
  });

  test('a 401 from the account check expires the session', () async {
    final gateway = _FakeGateway()..restoreResult = true;
    final container = _container(
      gateway,
      _FakeAccountSource([const ApiUnauthorized()]),
    );
    container.read(authControllerProvider);
    await _settle();

    final state = container.read(authControllerProvider);
    expect(state, isA<AuthSignedOut>());
    expect((state as AuthSignedOut).sessionExpired, isTrue);
    expect(gateway.signOutCalls, 1);
  });

  test('an unavailable account check keeps protected routes closed', () async {
    final gateway = _FakeGateway()..restoreResult = true;
    final container = _container(
      gateway,
      _FakeAccountSource([const ApiUnavailable(statusCode: 503)]),
    );
    container.read(authControllerProvider);
    await _settle();

    expect(
      decideAccess(container.read(authControllerProvider)),
      AccessDecision.accountStatus,
    );

    final source = _FakeAccountSource([const ApiSuccess(AccountStatus.active)]);
    final retryContainer = _container(gateway, source);
    retryContainer.read(authControllerProvider);
    await _settle();
    await retryContainer.read(authControllerProvider.notifier).retryAccountStatus();

    expect(
      decideAccess(retryContainer.read(authControllerProvider)),
      AccessDecision.home,
    );
  });

  test('sign-out returns to signed out even if the provider fails', () async {
    final gateway = _ThrowingSignOutGateway()..restoreResult = true;
    final container = _container(
      gateway,
      _FakeAccountSource([const ApiSuccess(AccountStatus.active)]),
    );
    container.read(authControllerProvider);
    await _settle();

    await container.read(authControllerProvider.notifier).signOut();

    final state = container.read(authControllerProvider);
    expect(state, isA<AuthSignedOut>());
    expect((state as AuthSignedOut).sessionExpired, isFalse);
  });
}

class _ThrowingRestoreGateway extends _FakeGateway {
  @override
  Future<bool> restoreSession() async {
    throw const AuthOperationException(AuthErrorKind.network);
  }
}

class _ThrowingSignOutGateway extends _FakeGateway {
  @override
  Future<void> signOut() async {
    throw const AuthOperationException(AuthErrorKind.network);
  }
}
