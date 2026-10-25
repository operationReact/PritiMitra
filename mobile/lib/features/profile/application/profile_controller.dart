import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_result.dart';
import '../../auth/application/auth_controller.dart';
import '../data/profile_api.dart';
import '../domain/own_profile.dart';

final profileApiProvider = Provider<ProfileApi>((ref) {
  return ProfileApi(client: ref.watch(apiClientProvider));
});

/// Loads the owner's own profile. Invalidate to retry after a failure.
final ownProfileProvider = FutureProvider.autoDispose<OwnProfile>((ref) async {
  final result = await ref.read(profileApiProvider).fetchOwnProfile();
  return switch (result) {
    ApiSuccess(:final data) => data,
    _ => throw ProfileLoadFailure(result),
  };
});

/// Carries a failed API outcome out of a provider so the UI can show a precise message.
class ProfileLoadFailure implements Exception {
  ProfileLoadFailure(this.result);

  final ApiResult<Object?> result;
}

/// Maps a load failure to a message that is safe to show. Never includes raw server text.
String profileErrorMessage(Object? error) {
  final failure = error is ProfileLoadFailure ? error.result : null;
  return switch (failure) {
    ApiUnauthorized() => 'Your session has expired. Sign in again to continue.',
    ApiForbidden() =>
      'This account cannot use profiles yet. Finish onboarding first.',
    _ => 'We could not reach PritiMitra. Check your connection and try again.',
  };
}

/// Maps a save or discoverability failure code to a user-facing message.
String profileActionErrorMessage(ApiResult<Object?> result) {
  return switch (result) {
    ApiForbidden(:final code) when code == 'PROFILE_INCOMPLETE' =>
      'Add a display name and at least one goal before turning on discovery.',
    ApiUnavailable(:final code) when code == 'PROFILE_INCOMPLETE' =>
      'Add a display name and at least one goal before turning on discovery.',
    ApiUnauthorized() => 'Your session has expired. Sign in again to continue.',
    ApiForbidden() => 'This change is not allowed for your account.',
    _ =>
      'Your changes could not be saved. Check your connection and try again.',
  };
}
