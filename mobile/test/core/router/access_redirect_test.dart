import 'package:flutter_test/flutter_test.dart';
import 'package:pritimitra/core/router/app_router.dart';
import 'package:pritimitra/features/auth/domain/account_status.dart';
import 'package:pritimitra/features/auth/domain/auth_state.dart';

void main() {
  const signedOut = AuthSignedOut();
  const loading = AuthInitializing();

  group('redirectForAccess', () {
    test('loading state keeps public pages and blocks protected ones', () {
      expect(
        redirectForAccess(location: AppRoutes.welcome, state: loading),
        isNull,
      );
      expect(
        redirectForAccess(location: AppRoutes.home, state: loading),
        AppRoutes.welcome,
      );
    });

    test('signed-out users reach public auth pages only', () {
      expect(
        redirectForAccess(location: AppRoutes.signIn, state: signedOut),
        isNull,
      );
      expect(
        redirectForAccess(location: AppRoutes.resetPassword, state: signedOut),
        isNull,
      );
      expect(
        redirectForAccess(location: AppRoutes.home, state: signedOut),
        AppRoutes.signIn,
      );
      expect(
        redirectForAccess(location: AppRoutes.accountStatus, state: signedOut),
        AppRoutes.signIn,
      );
    });

    test('query strings do not bypass the guard', () {
      expect(
        redirectForAccess(location: '/home?x=1', state: signedOut),
        AppRoutes.signIn,
      );
    });

    test('non-active accounts cannot reach home or public auth pages', () {
      const pending = AuthSignedIn(
        status: AccountStatus.pendingEmailVerification,
      );
      expect(
        redirectForAccess(location: AppRoutes.home, state: pending),
        AppRoutes.accountStatus,
      );
      expect(
        redirectForAccess(location: AppRoutes.signIn, state: pending),
        AppRoutes.accountStatus,
      );
      expect(
        redirectForAccess(location: AppRoutes.accountStatus, state: pending),
        isNull,
      );
      expect(
        redirectForAccess(location: AppRoutes.confirmSignUp, state: pending),
        isNull,
      );
    });

    test('unknown status shows account status with retry', () {
      expect(
        redirectForAccess(
          location: AppRoutes.home,
          state: const AuthSignedIn(),
        ),
        AppRoutes.accountStatus,
      );
    });

    test('ineligible users are confined to the ineligible screen', () {
      const ineligible = AuthSignedIn(status: AccountStatus.ineligible);
      expect(
        redirectForAccess(location: AppRoutes.home, state: ineligible),
        AppRoutes.ineligible,
      );
      expect(
        redirectForAccess(location: AppRoutes.accountStatus, state: ineligible),
        AppRoutes.ineligible,
      );
      expect(
        redirectForAccess(location: AppRoutes.ineligible, state: ineligible),
        isNull,
      );
    });

    test('active users are sent home and stay there', () {
      const active = AuthSignedIn(status: AccountStatus.active);
      expect(
        redirectForAccess(location: AppRoutes.welcome, state: active),
        AppRoutes.home,
      );
      expect(
        redirectForAccess(location: AppRoutes.signIn, state: active),
        AppRoutes.home,
      );
      expect(
        redirectForAccess(location: AppRoutes.home, state: active),
        isNull,
      );
    });

    test('active users can open discovery and profile', () {
      const active = AuthSignedIn(status: AccountStatus.active);
      expect(
        redirectForAccess(location: AppRoutes.discover, state: active),
        isNull,
      );
      expect(
        redirectForAccess(location: AppRoutes.profile, state: active),
        isNull,
      );
    });

    test('non-active users cannot open discovery or profile', () {
      const pending = AuthSignedIn(
        status: AccountStatus.pendingEmailVerification,
      );
      const suspended = AuthSignedIn(status: AccountStatus.suspended);
      for (final state in [pending, suspended]) {
        expect(
          redirectForAccess(location: AppRoutes.discover, state: state),
          isNot(isNull),
        );
        expect(
          redirectForAccess(location: AppRoutes.profile, state: state),
          isNot(isNull),
        );
      }
      expect(
        redirectForAccess(location: AppRoutes.profile, state: signedOut),
        AppRoutes.signIn,
      );
    });
  });
}
