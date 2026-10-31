import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'design_experience.dart';

final sharedPreferencesProvider = Provider<SharedPreferences>((ref) {
  throw UnimplementedError(
    'sharedPreferencesProvider must be overridden with an initialized instance.',
  );
});

final designExperienceProvider =
    NotifierProvider<DesignExperienceController, DesignExperience>(
      DesignExperienceController.new,
    );

class DesignExperienceController extends Notifier<DesignExperience> {
  @override
  DesignExperience build() {
    final stored = ref
        .watch(sharedPreferencesProvider)
        .getString(DesignExperiencePreference.storageKey);
    return DesignExperience.fromStorage(stored);
  }

  Future<void> setExperience(DesignExperience experience) async {
    state = experience;
    await ref
        .read(sharedPreferencesProvider)
        .setString(DesignExperiencePreference.storageKey, experience.storageValue);
  }
}
