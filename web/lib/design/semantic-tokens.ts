import type { DesignExperience } from "./experience";

export const PRITIMITRA_BRAND = {
  primary: "#7540f5",
  primaryDeep: "#6330b6",
  pink: "#ff3b8c",
  coral: "#ff6b70",
  navy: "#0b0f2d",
  muted: "#77758a",
  border: "#e6e3ee",
  background: "#fffcfb",
  surface: "#ffffff",
} as const;

export type ExperienceSemanticTokens = {
  colors: {
    background: string;
    surface: string;
    textPrimary: string;
    textMuted: string;
    border: string;
    accent: string;
    privacyAccent: string;
  };
  spacing: {
    unit: number;
    sectionGap: number;
    contentGap: number;
  };
  radius: {
    control: number;
    card: number;
    sheet: number;
  };
  elevation: {
    surface: number;
    overlay: number;
  };
  typography: {
    headingWeight: number;
    titleTracking: number;
    bodyLineHeight: number;
  };
  motion: {
    fastMs: number;
    standardMs: number;
    emphasisMs: number;
  };
  density: {
    scale: number;
  };
};

export const EXPERIENCE_SEMANTIC_TOKENS: Record<
  DesignExperience,
  ExperienceSemanticTokens
> = {
  spark: {
    colors: {
      background: PRITIMITRA_BRAND.background,
      surface: PRITIMITRA_BRAND.surface,
      textPrimary: PRITIMITRA_BRAND.navy,
      textMuted: PRITIMITRA_BRAND.muted,
      border: PRITIMITRA_BRAND.border,
      accent: PRITIMITRA_BRAND.pink,
      privacyAccent: PRITIMITRA_BRAND.primaryDeep,
    },
    spacing: { unit: 8, sectionGap: 24, contentGap: 16 },
    radius: { control: 18, card: 22, sheet: 28 },
    elevation: { surface: 2, overlay: 12 },
    typography: { headingWeight: 800, titleTracking: -0.5, bodyLineHeight: 1.5 },
    motion: { fastMs: 160, standardMs: 280, emphasisMs: 420 },
    density: { scale: 1 },
  },
  essence: {
    colors: {
      background: PRITIMITRA_BRAND.background,
      surface: PRITIMITRA_BRAND.surface,
      textPrimary: PRITIMITRA_BRAND.navy,
      textMuted: PRITIMITRA_BRAND.muted,
      border: PRITIMITRA_BRAND.border,
      accent: PRITIMITRA_BRAND.primaryDeep,
      privacyAccent: PRITIMITRA_BRAND.primary,
    },
    spacing: { unit: 10, sectionGap: 32, contentGap: 20 },
    radius: { control: 14, card: 16, sheet: 22 },
    elevation: { surface: 0, overlay: 6 },
    typography: { headingWeight: 700, titleTracking: -0.2, bodyLineHeight: 1.6 },
    motion: { fastMs: 120, standardMs: 200, emphasisMs: 260 },
    density: { scale: 0.96 },
  },
};

export function semanticTokensFor(
  experience: DesignExperience,
): ExperienceSemanticTokens {
  return EXPERIENCE_SEMANTIC_TOKENS[experience];
}
