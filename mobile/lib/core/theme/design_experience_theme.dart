import 'package:flutter/material.dart';

import 'app_theme.dart';
import 'design_experience.dart';

abstract final class DesignExperienceTheme {
  static ThemeData forExperience(DesignExperience experience) {
    return switch (experience) {
      DesignExperience.spark => AppTheme.light(),
      DesignExperience.essence => AppTheme.light().copyWith(
        colorScheme: AppTheme.light().colorScheme.copyWith(
          primary: const Color(0xFF5A365F),
          secondary: const Color(0xFF8B6B7E),
          surface: const Color(0xFFFFFBFC),
          onSurface: const Color(0xFF251E25),
        ),
        scaffoldBackgroundColor: const Color(0xFFFFFBFC),
        cardTheme: AppTheme.light().cardTheme.copyWith(
          color: const Color(0xFFFFFBFC),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        ),
      ),
    };
  }
}
