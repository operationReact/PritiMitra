import 'package:flutter/material.dart';

import 'app_colors.dart';
import 'design_experience.dart';

@immutable
final class DesignExperienceTokens extends ThemeExtension<DesignExperienceTokens> {
  const DesignExperienceTokens({
    required this.background,
    required this.surface,
    required this.textPrimary,
    required this.textMuted,
    required this.border,
    required this.accent,
    required this.privacyAccent,
    required this.spacingUnit,
    required this.sectionGap,
    required this.cardRadius,
    required this.sheetRadius,
    required this.surfaceElevation,
    required this.headingWeight,
    required this.titleLetterSpacing,
    required this.motionFast,
    required this.motionStandard,
    required this.motionEmphasis,
    required this.densityScale,
  });

  final Color background;
  final Color surface;
  final Color textPrimary;
  final Color textMuted;
  final Color border;
  final Color accent;
  final Color privacyAccent;
  final double spacingUnit;
  final double sectionGap;
  final BorderRadius cardRadius;
  final BorderRadius sheetRadius;
  final double surfaceElevation;
  final FontWeight headingWeight;
  final double titleLetterSpacing;
  final Duration motionFast;
  final Duration motionStandard;
  final Duration motionEmphasis;
  final double densityScale;

  static const spark = DesignExperienceTokens(
    background: AppColors.background,
    surface: AppColors.surface,
    textPrimary: AppColors.navy,
    textMuted: AppColors.muted,
    border: AppColors.border,
    accent: AppColors.pink,
    privacyAccent: AppColors.primaryDeep,
    spacingUnit: 8,
    sectionGap: 24,
    cardRadius: BorderRadius.all(Radius.circular(22)),
    sheetRadius: BorderRadius.all(Radius.circular(28)),
    surfaceElevation: 2,
    headingWeight: FontWeight.w800,
    titleLetterSpacing: -0.5,
    motionFast: Duration(milliseconds: 160),
    motionStandard: Duration(milliseconds: 280),
    motionEmphasis: Duration(milliseconds: 420),
    densityScale: 1,
  );

  static const essence = DesignExperienceTokens(
    background: AppColors.background,
    surface: AppColors.surface,
    textPrimary: AppColors.navy,
    textMuted: AppColors.muted,
    border: AppColors.border,
    accent: AppColors.primaryDeep,
    privacyAccent: AppColors.primary,
    spacingUnit: 10,
    sectionGap: 32,
    cardRadius: BorderRadius.all(Radius.circular(16)),
    sheetRadius: BorderRadius.all(Radius.circular(22)),
    surfaceElevation: 0,
    headingWeight: FontWeight.w700,
    titleLetterSpacing: -0.2,
    motionFast: Duration(milliseconds: 120),
    motionStandard: Duration(milliseconds: 200),
    motionEmphasis: Duration(milliseconds: 260),
    densityScale: 0.96,
  );

  static DesignExperienceTokens forExperience(DesignExperience experience) {
    return switch (experience) {
      DesignExperience.spark => spark,
      DesignExperience.essence => essence,
    };
  }

  @override
  DesignExperienceTokens copyWith({
    Color? background,
    Color? surface,
    Color? textPrimary,
    Color? textMuted,
    Color? border,
    Color? accent,
    Color? privacyAccent,
    double? spacingUnit,
    double? sectionGap,
    BorderRadius? cardRadius,
    BorderRadius? sheetRadius,
    double? surfaceElevation,
    FontWeight? headingWeight,
    double? titleLetterSpacing,
    Duration? motionFast,
    Duration? motionStandard,
    Duration? motionEmphasis,
    double? densityScale,
  }) {
    return DesignExperienceTokens(
      background: background ?? this.background,
      surface: surface ?? this.surface,
      textPrimary: textPrimary ?? this.textPrimary,
      textMuted: textMuted ?? this.textMuted,
      border: border ?? this.border,
      accent: accent ?? this.accent,
      privacyAccent: privacyAccent ?? this.privacyAccent,
      spacingUnit: spacingUnit ?? this.spacingUnit,
      sectionGap: sectionGap ?? this.sectionGap,
      cardRadius: cardRadius ?? this.cardRadius,
      sheetRadius: sheetRadius ?? this.sheetRadius,
      surfaceElevation: surfaceElevation ?? this.surfaceElevation,
      headingWeight: headingWeight ?? this.headingWeight,
      titleLetterSpacing: titleLetterSpacing ?? this.titleLetterSpacing,
      motionFast: motionFast ?? this.motionFast,
      motionStandard: motionStandard ?? this.motionStandard,
      motionEmphasis: motionEmphasis ?? this.motionEmphasis,
      densityScale: densityScale ?? this.densityScale,
    );
  }

  @override
  DesignExperienceTokens lerp(
    covariant DesignExperienceTokens? other,
    double t,
  ) {
    if (other == null) return this;
    return t < 0.5 ? this : other;
  }
}
