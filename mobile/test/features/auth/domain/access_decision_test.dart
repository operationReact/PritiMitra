import 'package:flutter_test/flutter_test.dart';
import 'package:pritimitra/features/auth/domain/account_status.dart';
import 'package:pritimitra/features/auth/domain/auth_state.dart';

void main() {
  group('decideAccess', () {
    test('initializing shows loading', () {
      expect(decideAccess(const AuthInitializing()), AccessDecision.loading);
    });

    test('signed-out and session-expired users go to sign-in', () {
      expect(decideAccess(const AuthSignedOut()), AccessDecision.signIn);
      expect(
        decideAccess(const AuthSignedOut(sessionExpired: true)),
        AccessDecision.signIn,
      );
    });

    test('unknown account status keeps protected routes closed', () {
      expect(
        decideAccess(const AuthSignedIn()),
        AccessDecision.accountStatus,
      );
    });

    test('only ACTIVE reaches home', () {
      expect(
        decideAccess(const AuthSignedIn(status: AccountStatus.active)),
        AccessDecision.home,
      );
    });

    test('INELIGIBLE gets the dedicated screen', () {
      expect(
        decideAccess(const AuthSignedIn(status: AccountStatus.ineligible)),
        AccessDecision.ineligible,
      );
    });

    test('onboarding-incomplete users go to the onboarding flow', () {
      expect(
        decideAccess(
          const AuthSignedIn(status: AccountStatus.onboardingIncomplete),
        ),
        AccessDecision.onboarding,
      );
    });

    test('pending and suspended users see account status', () {
      for (final status in [
        AccountStatus.pendingEmailVerification,
        AccountStatus.suspended,
      ]) {
        expect(
          decideAccess(AuthSignedIn(status: status)),
          AccessDecision.accountStatus,
          reason: status.name,
        );
      }
    });
  });

  group('AccountStatus', () {
    test('parses every backend wire value', () {
      expect(
        AccountStatus.fromWire('PENDING_EMAIL_VERIFICATION'),
        AccountStatus.pendingEmailVerification,
      );
      expect(
        AccountStatus.fromWire('ONBOARDING_INCOMPLETE'),
        AccountStatus.onboardingIncomplete,
      );
      expect(AccountStatus.fromWire('ACTIVE'), AccountStatus.active);
      expect(AccountStatus.fromWire('INELIGIBLE'), AccountStatus.ineligible);
      expect(AccountStatus.fromWire('SUSPENDED'), AccountStatus.suspended);
    });

    test('unknown or missing values parse to null', () {
      expect(AccountStatus.fromWire('SOMETHING_NEW'), isNull);
      expect(AccountStatus.fromWire(null), isNull);
    });

    test('maps known 403 codes and ignores others', () {
      expect(
        accountStatusForForbiddenCode('AGE_INELIGIBLE'),
        AccountStatus.ineligible,
      );
      expect(
        accountStatusForForbiddenCode('EMAIL_NOT_VERIFIED'),
        AccountStatus.pendingEmailVerification,
      );
      expect(
        accountStatusForForbiddenCode('ACCOUNT_SUSPENDED'),
        AccountStatus.suspended,
      );
      expect(accountStatusForForbiddenCode('SOMETHING_ELSE'), isNull);
      expect(accountStatusForForbiddenCode(null), isNull);
    });
  });
}
