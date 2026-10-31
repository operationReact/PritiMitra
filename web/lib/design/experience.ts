export const DESIGN_EXPERIENCES = ["spark", "essence"] as const;

export type DesignExperience = (typeof DESIGN_EXPERIENCES)[number];

export const DEFAULT_DESIGN_EXPERIENCE: DesignExperience = "spark";
export const DESIGN_EXPERIENCE_STORAGE_KEY = "pritimitra.design-experience.v1";

export const DESIGN_EXPERIENCE_META: Record<
  DesignExperience,
  { label: string; description: string }
> = {
  spark: {
    label: "Spark",
    description: "Vibrant, expressive and playful with immersive gradients.",
  },
  essence: {
    label: "Essence",
    description: "Editorial, refined and calm with privacy-forward surfaces.",
  },
};

export function isDesignExperience(value: unknown): value is DesignExperience {
  return typeof value === "string" && DESIGN_EXPERIENCES.includes(value as DesignExperience);
}
