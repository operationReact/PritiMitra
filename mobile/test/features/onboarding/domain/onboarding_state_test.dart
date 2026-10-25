import 'package:flutter_test/flutter_test.dart';
import 'package:pritimitra/features/auth/domain/account_status.dart';
import 'package:pritimitra/features/onboarding/domain/onboarding_state.dart';
import 'package:pritimitra/features/onboarding/domain/profile_draft.dart';

OnboardingState _state({
  bool ageVerified = false,
  bool consents = false,
  List<String> missing = const [],
}) {
  return OnboardingState(
    status: AccountStatus.onboardingIncomplete,
    ageVerified: ageVerified,
    requiredConsentsAccepted: consents,
    discoveryPreferencesConfigured: false,
    missingFields: missing,
  );
}

void main() {
  group('nextOnboardingStep', () {
    test('starts with date of birth', () {
      expect(nextOnboardingStep(_state()), OnboardingStep.dateOfBirth);
    });

    test('moves to terms after age verification', () {
      expect(
        nextOnboardingStep(_state(ageVerified: true)),
        OnboardingStep.terms,
      );
    });

    test('moves to goals, then profile, then review', () {
      expect(
        nextOnboardingStep(_state(
          ageVerified: true,
          consents: true,
          missing: const [missingConnectionGoals, missingDisplayName],
        )),
        OnboardingStep.goals,
      );
      expect(
        nextOnboardingStep(_state(
          ageVerified: true,
          consents: true,
          missing: const [missingDisplayName],
        )),
        OnboardingStep.profile,
      );
      expect(
        nextOnboardingStep(_state(ageVerified: true, consents: true)),
        OnboardingStep.review,
      );
    });
  });

  group('OnboardingState.fromJson', () {
    test('parses a valid reply', () {
      final state = OnboardingState.fromJson({
        'status': 'ONBOARDING_INCOMPLETE',
        'ageVerified': true,
        'requiredConsentsAccepted': false,
        'discoveryPreferencesConfigured': false,
        'missingFields': ['displayName'],
      });
      expect(state, isNotNull);
      expect(state!.status, AccountStatus.onboardingIncomplete);
      expect(state.ageVerified, isTrue);
      expect(state.missingFields, ['displayName']);
    });

    test('rejects malformed replies instead of guessing progress', () {
      expect(OnboardingState.fromJson({'status': 'ACTIVE'}), isNull);
      expect(
        OnboardingState.fromJson({
          'ageVerified': 'yes',
          'requiredConsentsAccepted': true,
          'discoveryPreferencesConfigured': true,
          'missingFields': <String>[],
        }),
        isNull,
      );
    });
  });

  group('ProfileDraft', () {
    test('serialises goals and keeps every visibility flag private', () {
      const draft = ProfileDraft(
        displayName: 'Asha',
        bio: 'Hello',
        goals: {ConnectionGoal.friendship, ConnectionGoal.dating},
      );
      final json = draft.toJson();
      expect(json['displayName'], 'Asha');
      expect((json['goals'] as List).toSet(), {'FRIENDSHIP', 'DATING'});
      expect(json['genderIdentityVisible'], isFalse);
      expect(json['pronounsVisible'], isFalse);
      expect(json['sexualOrientationVisible'], isFalse);
    });

    test('ignores unknown goal codes when parsing', () {
      final draft = ProfileDraft.fromJson({
        'displayName': 'Asha',
        'goals': ['COMMUNITY', 'NOT_A_GOAL'],
      });
      expect(draft, isNotNull);
      expect(draft!.goals, {ConnectionGoal.community});
    });

    test('returns null when goals are not a list', () {
      expect(ProfileDraft.fromJson({'goals': 'DATING'}), isNull);
    });
  });
}
