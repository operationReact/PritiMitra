import { describe, expect, it } from "vitest";
import {
  DEFAULT_DESIGN_EXPERIENCE,
  DESIGN_EXPERIENCES,
  isDesignExperience,
} from "./experience";

describe("design experience", () => {
  it("defaults safely to Spark", () => {
    expect(DEFAULT_DESIGN_EXPERIENCE).toBe("spark");
  });

  it("accepts only supported experience identifiers", () => {
    expect(DESIGN_EXPERIENCES).toEqual(["spark", "essence"]);
    expect(isDesignExperience("spark")).toBe(true);
    expect(isDesignExperience("essence")).toBe(true);
    expect(isDesignExperience("neumorphism")).toBe(false);
    expect(isDesignExperience(null)).toBe(false);
  });
});
