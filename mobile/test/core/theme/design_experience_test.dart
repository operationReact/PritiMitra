import 'package:flutter_test/flutter_test.dart';
import 'package:pritimitra/core/theme/app_colors.dart';
import 'package:pritimitra/core/theme/design_experience.dart';
import 'package:pritimitra/core/theme/design_experience_theme.dart';

void main() {
  group('DesignExperience', () {
    test('uses Spark as the safe default', () {
      expect(DesignExperiencePreference.defaultExperience, DesignExperience.spark);
      expect(DesignExperience.fromStorage(null), DesignExperience.spark);
      expect(DesignExperience.fromStorage('unknown'), DesignExperience.spark);
    });

    test('round trips supported storage values', () {
      for (final experience in DesignExperience.values) {
        expect(
          DesignExperience.fromStorage(experience.storageValue),
          experience,
        );
      }
    });

    test('keeps approved PritiMitra brand tokens invariant', () {
      for (final experience in DesignExperience.values) {
        final theme = DesignExperienceTheme.forExperience(experience);

        expect(theme.colorScheme.primary, AppColors.primary);
        expect(theme.scaffoldBackgroundColor, AppColors.background);
        expect(theme.cardTheme.color, AppColors.surface);
      }
    });
  });
}
