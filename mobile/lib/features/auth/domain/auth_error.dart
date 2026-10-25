/// Categories of auth failure. Screens show generic copy and never expose
/// provider detail, so account existence cannot be inferred.
enum AuthErrorKind {
  invalidCredentials,
  userNotConfirmed,
  usernameExists,
  invalidPassword,
  invalidCode,
  expiredCode,
  limitExceeded,
  invalidInput,
  network,
  notConfigured,
  unknown,
}

class AuthOperationException implements Exception {
  const AuthOperationException(this.kind);

  final AuthErrorKind kind;

  String get userMessage => authErrorMessage(kind);

  @override
  String toString() => 'AuthOperationException(${kind.name})';
}

String authErrorMessage(AuthErrorKind kind) {
  return switch (kind) {
    AuthErrorKind.invalidCredentials => 'Email or password is incorrect.',
    AuthErrorKind.userNotConfirmed => 'Please confirm your email to continue.',
    AuthErrorKind.usernameExists =>
      'We could not create an account with those details.',
    AuthErrorKind.invalidPassword =>
      'Choose a stronger password and try again.',
    AuthErrorKind.invalidCode => 'That code is not right. Please try again.',
    AuthErrorKind.expiredCode => 'That code has expired. Request a new one.',
    AuthErrorKind.limitExceeded =>
      'Too many attempts. Please wait a few minutes and try again.',
    AuthErrorKind.invalidInput => 'Please check the details and try again.',
    AuthErrorKind.network =>
      'We could not reach PritiMitra. Check your connection and try again.',
    AuthErrorKind.notConfigured =>
      'Sign-in is not available in this build.',
    AuthErrorKind.unknown => 'Something went wrong. Please try again.',
  };
}
