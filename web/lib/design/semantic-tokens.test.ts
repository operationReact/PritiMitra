import { describe, expect, it } from "vitest";
import {
  EXPERIENCE_SEMANTIC_TOKENS,
  PRITIMITRA_BRAND,
  semanticTokensFor,
} from "./semantic-tokens";

describe("experience semantic tokens", () => {
  it("preserves immutable PritiMitra brand roles across experiences", () => {
    const spark = semanticTokensFor("spark");
    const essence = semanticTokensFor("essence");

    for (const tokens of [spark, essence]) {
      expect(tokens.colors.background).toBe(PRITIMITRA_BRAND.background);
      expect(tokens.colors.surface).toBe(PRITIMITRA_BRAND.surface);
      expect(tokens.colors.textPrimary).toBe(PRITIMITRA_BRAND.navy);
      expect(tokens.colors.textMuted).toBe(PRITIMITRA_BRAND.muted);
      expect(tokens.colors.border).toBe(PRITIMITRA_BRAND.border);
    }
  });

  it("gives Spark and Essence distinct composition semantics", () => {
    const { spark, essence } = EXPERIENCE_SEMANTIC_TOKENS;

    expect(spark.spacing.sectionGap).not.toBe(essence.spacing.sectionGap);
    expect(spark.radius.card).not.toBe(essence.radius.card);
    expect(spark.motion.standardMs).not.toBe(essence.motion.standardMs);
    expect(spark.typography.headingWeight).not.toBe(
      essence.typography.headingWeight,
    );
    expect(spark.density.scale).not.toBe(essence.density.scale);
  });

  it("only uses approved shared brand colors for semantic accents", () => {
    const approved = new Set<string>(Object.values(PRITIMITRA_BRAND));

    for (const tokens of Object.values(EXPERIENCE_SEMANTIC_TOKENS)) {
      expect(approved.has(tokens.colors.accent)).toBe(true);
      expect(approved.has(tokens.colors.privacyAccent)).toBe(true);
    }
  });
});
