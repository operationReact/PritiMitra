import 'account_status.dart';

/// Session state the router reads.
///
/// [AuthSignedIn.status] is null when the backend account status could not be
/// loaded. That is treated as "not active" so protected routes stay closed.
sealed class AuthState {
  const AuthState();
}

final class AuthInitializing extends AuthState {
  const AuthInitializing();
}

final class AuthSignedOut extends AuthState {
  const AuthSignedOut({this.sessionExpired = false});

  /// True when the user was signed out because the session could no longer be refreshed.
  final bool sessionExpired;
}

final class AuthSignedIn extends AuthState {
  const AuthSignedIn({this.status});

  final AccountStatus? status;
}

/// Where the router should send the user.
enum AccessDecision { loading, signIn, accountStatus, ineligible, home }

AccessDecision decideAccess(AuthState state) {
  return switch (state) {
    AuthInitializing() => AccessDecision.loading,
    AuthSignedOut() => AccessDecision.signIn,
    AuthSignedIn(status: null) => AccessDecision.accountStatus,
    AuthSignedIn(status: AccountStatus.active) => AccessDecision.home,
    AuthSignedIn(status: AccountStatus.ineligible) => AccessDecision.ineligible,
    AuthSignedIn() => AccessDecision.accountStatus,
  };
}
