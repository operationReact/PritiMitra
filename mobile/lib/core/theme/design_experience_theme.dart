import 'package:flutter/material.dart';

import 'app_theme.dart';
import 'design_experience.dart';
import 'design_experience_tokens.dart';

abstract final class DesignExperienceTheme {
  static ThemeData forExperience(DesignExperience experience) {
    // Spark and Essence preserve the approved PritiMitra brand palette. Their
    // semantic differences are expressed through the experience token
    // extension so product screens can vary composition, spacing, density,
    // surfaces, typography hierarchy and motion without duplicating business
    // logic or replacing core brand identity.
    final base = AppTheme.light();
    final tokens = DesignExperienceTokens.forExperience(experience);

    return base.copyWith(
      extensions: <ThemeExtension<dynamic>>[tokens],
      visualDensity: VisualDensity(
        horizontal: (tokens.densityScale - 1) * 4,
        vertical: (tokens.densityScale - 1) * 4,
      ),
    );
  }
}
