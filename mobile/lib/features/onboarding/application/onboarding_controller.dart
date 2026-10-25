import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/config/app_config.dart';
import '../../../core/network/api_result.dart';
import '../../auth/application/auth_controller.dart';
import '../data/onboarding_api.dart';
import '../domain/onboarding_state.dart';
import '../domain/profile_draft.dart';

final onboardingApiProvider = Provider<OnboardingApi>((ref) {
  return OnboardingApi(client: ref.watch(apiClientProvider));
});

final onboardingControllerProvider =
    NotifierProvider<OnboardingController, OnboardingFlowState>(
  OnboardingController.new,
);

enum OnboardingLoad { loading, ready, failed }

/// Client-side reasons a step did not advance. Server rules are reported as [rejected] or [conflict].
enum OnboardingProblem {
  network,
  rejected,
  conflict,
  profileIncomplete,
  policyUnavailable,
  goalRequired,
  nameRequired,
}

class OnboardingFlowState {
  const OnboardingFlowState({
    this.load = OnboardingLoad.loading,
    this.step,
    this.draft = const ProfileDraft(),
    this.busy = false,
    this.problem,
  });

  final OnboardingLoad load;

  /// The first unmet server step. Null until the state has loaded.
  final OnboardingStep? step;

  final ProfileDraft draft;
  final bool busy;
  final OnboardingProblem? problem;

  OnboardingFlowState copyWith({
    OnboardingLoad? load,
    OnboardingStep? step,
    ProfileDraft? draft,
    bool? busy,
    OnboardingProblem? problem,
    bool clearProblem = false,
  }) {
    return OnboardingFlowState(
      load: load ?? this.load,
      step: step ?? this.step,
      draft: draft ?? this.draft,
      busy: busy ?? this.busy,
      problem: clearProblem ? null : (problem ?? this.problem),
    );
  }
}

/// Drives the onboarding steps. The server decides every rule. The controller only sends requests,
/// advances to the step the server reports, and reports failures.
class OnboardingController extends Notifier<OnboardingFlowState> {
  @override
  OnboardingFlowState build() {
    Future<void>.microtask(load);
    return const OnboardingFlowState();
  }

  OnboardingApi get _api => ref.read(onboardingApiProvider);

  Future<void> load() async {
    state = state.copyWith(load: OnboardingLoad.loading, busy: false, clearProblem: true);
    final stateResult = await _api.fetchState();
    if (stateResult is! ApiSuccess<OnboardingState>) {
      await _handleFailure(stateResult);
      return;
    }
    final profileResult = await _api.fetchProfile();
    if (profileResult is! ApiSuccess<ProfileDraft>) {
      await _handleFailure(profileResult);
      return;
    }
    state = state.copyWith(
      load: OnboardingLoad.ready,
      step: nextOnboardingStep(stateResult.data),
      draft: profileResult.data,
      clearProblem: true,
    );
  }

  Future<void> submitDateOfBirth(DateTime dateOfBirth) {
    return _runStep(() => _api.submitDateOfBirth(dateOfBirth));
  }

  /// Fails closed when policy versions are not configured. No version is invented.
  Future<void> acceptConsents() {
    final config = ref.read(appConfigProvider);
    if (!config.hasPolicyVersions) {
      state = state.copyWith(busy: false, problem: OnboardingProblem.policyUnavailable);
      return Future.value();
    }
    return _runStep(
      () => _api.acceptConsents(
        termsVersion: config.termsVersion,
        privacyVersion: config.privacyVersion,
      ),
    );
  }

  Future<void> saveGoals(Set<ConnectionGoal> goals) {
    if (goals.isEmpty) {
      state = state.copyWith(problem: OnboardingProblem.goalRequired);
      return Future.value();
    }
    final draft = state.draft.copyWith(goals: goals);
    return _runStep(() => _api.saveProfile(draft), draft: draft);
  }

  Future<void> saveProfile({required String displayName, required String bio}) {
    final name = displayName.trim();
    if (name.isEmpty) {
      state = state.copyWith(problem: OnboardingProblem.nameRequired);
      return Future.value();
    }
    final trimmedBio = bio.trim();
    final draft = state.draft.copyWith(
      displayName: name,
      bio: trimmedBio.isEmpty ? null : trimmedBio,
    );
    return _runStep(() => _api.saveProfile(draft), draft: draft);
  }

  /// Activates the account. The auth controller re-reads the status, so the router moves the user to home.
  Future<void> complete() async {
    state = state.copyWith(busy: true, clearProblem: true);
    final result = await _api.complete();
    switch (result) {
      case ApiSuccess():
        state = state.copyWith(busy: false);
        await ref.read(authControllerProvider.notifier).retryAccountStatus();
      case ApiForbidden(:final code):
        await _handleForbidden(code);
      case ApiUnauthorized():
        await ref.read(authControllerProvider.notifier).expireSession();
      case ApiUnavailable(:final statusCode) when statusCode == 409:
        state = state.copyWith(busy: false, problem: OnboardingProblem.profileIncomplete);
      case ApiUnavailable():
        state = state.copyWith(busy: false, problem: OnboardingProblem.network);
    }
  }

  Future<void> _runStep(
    Future<ApiResult<OnboardingState>> Function() call, {
    ProfileDraft? draft,
  }) async {
    state = state.copyWith(busy: true, clearProblem: true);
    final result = await call();
    switch (result) {
      case ApiSuccess(:final data):
        state = state.copyWith(
          busy: false,
          step: nextOnboardingStep(data),
          draft: draft ?? state.draft,
        );
      case ApiForbidden(:final code):
        await _handleForbidden(code);
      case ApiUnauthorized():
        await ref.read(authControllerProvider.notifier).expireSession();
      case ApiUnavailable(:final statusCode):
        state = state.copyWith(busy: false, problem: _problemForStatus(statusCode));
    }
  }

  /// Ineligible, unverified and suspended responses change the account state, so re-read the status and let the router decide.
  Future<void> _handleForbidden(String? code) async {
    final known = code == 'AGE_INELIGIBLE' ||
        code == 'EMAIL_NOT_VERIFIED' ||
        code == 'ACCOUNT_SUSPENDED';
    if (known) {
      await ref.read(authControllerProvider.notifier).retryAccountStatus();
      return;
    }
    state = state.copyWith(busy: false, problem: OnboardingProblem.rejected);
  }

  Future<void> _handleFailure<T>(ApiResult<T> result) async {
    switch (result) {
      case ApiSuccess():
        return;
      case ApiForbidden(:final code):
        state = state.copyWith(load: OnboardingLoad.failed);
        await _handleForbidden(code);
      case ApiUnauthorized():
        await ref.read(authControllerProvider.notifier).expireSession();
      case ApiUnavailable():
        state = state.copyWith(
          load: OnboardingLoad.failed,
          busy: false,
          problem: OnboardingProblem.network,
        );
    }
  }

  static OnboardingProblem _problemForStatus(int? statusCode) {
    return switch (statusCode) {
      400 => OnboardingProblem.rejected,
      409 => OnboardingProblem.conflict,
      _ => OnboardingProblem.network,
    };
  }
}
