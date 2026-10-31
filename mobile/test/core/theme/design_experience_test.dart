import 'package:flutter_test/flutter_test.dart';
import 'package:pritimitra/core/theme/app_colors.dart';
import 'package:pritimitra/core/theme/design_experience.dart';
import 'package:pritimitra/core/theme/design_experience_theme.dart';
import 'package:pritimitra/core/theme/design_experience_tokens.dart';

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

    test('attaches semantic tokens for every experience', () {
      for (final experience in DesignExperience.values) {
        final theme = DesignExperienceTheme.forExperience(experience);
        final tokens = theme.extension<DesignExperienceTokens>();

        expect(tokens, isNotNull);
        expect(tokens!.background, AppColors.background);
        expect(tokens.surface, AppColors.surface);
        expect(tokens.textPrimary, AppColors.navy);
        expect(tokens.textMuted, AppColors.muted);
        expect(tokens.border, AppColors.border);
      }
    });

    test('differentiates composition semantics without replacing brand DNA', () {
      const spark = DesignExperienceTokens.spark;
      const essence = DesignExperienceTokens.essence;

      expect(spark.background, essence.background);
      expect(spark.surface, essence.surface);
      expect(spark.textPrimary, essence.textPrimary);
      expect(spark.sectionGap, isNot(equals(essence.sectionGap)));
      expect(spark.cardRadius, isNot(equals(essence.cardRadius)));
      expect(spark.motionStandard, isNot(equals(essence.motionStandard)));
      expect(spark.headingWeight, isNot(equals(essence.headingWeight)));
    });
  });
}
