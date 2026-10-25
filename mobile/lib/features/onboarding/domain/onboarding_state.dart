import '../../auth/domain/account_status.dart';

/// Server-side onboarding progress from `GET /api/v1/onboarding`.
///
/// Progress lives on the backend, so resuming after a restart reads this state
/// again instead of trusting local storage.
class OnboardingState {
  const OnboardingState({
    required this.status,
    required this.ageVerified,
    required this.requiredConsentsAccepted,
    required this.discoveryPreferencesConfigured,
    required this.missingFields,
  });

  final AccountStatus? status;
  final bool ageVerified;
  final bool requiredConsentsAccepted;
  final bool discoveryPreferencesConfigured;
  final List<String> missingFields;

  /// Returns null when required keys are missing, so a malformed reply is never treated as progress.
  static OnboardingState? fromJson(Map<String, dynamic> json) {
    final ageVerified = json['ageVerified'];
    final consents = json['requiredConsentsAccepted'];
    final discovery = json['discoveryPreferencesConfigured'];
    final missing = json['missingFields'];
    if (ageVerified is! bool ||
        consents is! bool ||
        discovery is! bool ||
        missing is! List) {
      return null;
    }
    return OnboardingState(
      status: AccountStatus.fromWire(json['status'] as String?),
      ageVerified: ageVerified,
      requiredConsentsAccepted: consents,
      discoveryPreferencesConfigured: discovery,
      missingFields: missing.whereType<String>().toList(growable: false),
    );
  }
}

/// The onboarding screen to show next. Each step is driven by server state.
enum OnboardingStep { dateOfBirth, terms, goals, profile, review }

/// Server field names reported in `missingFields`.
const missingDisplayName = 'displayName';
const missingConnectionGoals = 'connectionGoals';

/// Picks the first required step that the server has not yet satisfied.
OnboardingStep nextOnboardingStep(OnboardingState state) {
  if (!state.ageVerified) {
    return OnboardingStep.dateOfBirth;
  }
  if (!state.requiredConsentsAccepted) {
    return OnboardingStep.terms;
  }
  if (state.missingFields.contains(missingConnectionGoals)) {
    return OnboardingStep.goals;
  }
  if (state.missingFields.contains(missingDisplayName)) {
    return OnboardingStep.profile;
  }
  return OnboardingStep.review;
}
