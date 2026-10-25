/// Backend account lifecycle states from `GET /api/v1/me`.
enum AccountStatus {
  pendingEmailVerification('PENDING_EMAIL_VERIFICATION'),
  onboardingIncomplete('ONBOARDING_INCOMPLETE'),
  active('ACTIVE'),
  ineligible('INELIGIBLE'),
  suspended('SUSPENDED');

  const AccountStatus(this.wireValue);

  final String wireValue;

  /// Returns null for unknown values so that a newer backend status never grants access.
  static AccountStatus? fromWire(String? value) {
    for (final status in values) {
      if (status.wireValue == value) {
        return status;
      }
    }
    return null;
  }
}

/// Maps a 403 business code to the account status it implies. Returns null for unknown codes.
AccountStatus? accountStatusForForbiddenCode(String? code) {
  return switch (code) {
    'AGE_INELIGIBLE' => AccountStatus.ineligible,
    'EMAIL_NOT_VERIFIED' => AccountStatus.pendingEmailVerification,
    'ACCOUNT_SUSPENDED' => AccountStatus.suspended,
    _ => null,
  };
}
