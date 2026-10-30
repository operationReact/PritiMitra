import 'package:flutter/material.dart';

import 'app_theme.dart';
import 'design_experience.dart';

abstract final class DesignExperienceTheme {
  static ThemeData forExperience(DesignExperience experience) {
    // Spark and Essence must preserve the same approved PritiMitra brand
    // identity. Experience-specific visual differences belong in semantic
    // composition, typography hierarchy, spacing, density, surfaces and
    // motion layers added by later slices; they must not silently replace the
    // canonical brand palette.
    return switch (experience) {
      DesignExperience.spark => AppTheme.light(),
      DesignExperience.essence => AppTheme.light(),
    };
  }
}
