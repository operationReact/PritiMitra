"use client";

import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import {
  DEFAULT_DESIGN_EXPERIENCE,
  DESIGN_EXPERIENCE_STORAGE_KEY,
  isDesignExperience,
  type DesignExperience,
} from "@/lib/design/experience";

type DesignExperienceContextValue = {
  experience: DesignExperience;
  setExperience: (experience: DesignExperience) => void;
};

const DesignExperienceContext = createContext<DesignExperienceContextValue | null>(null);

function applyExperience(experience: DesignExperience) {
  document.documentElement.dataset.experience = experience;
}

function readInitialExperience(): DesignExperience {
  if (typeof window === "undefined") return DEFAULT_DESIGN_EXPERIENCE;
  const stored = window.localStorage.getItem(DESIGN_EXPERIENCE_STORAGE_KEY);
  return isDesignExperience(stored) ? stored : DEFAULT_DESIGN_EXPERIENCE;
}

export function DesignExperienceProvider({ children }: { children: ReactNode }) {
  const [experience, setExperienceState] = useState<DesignExperience>(readInitialExperience);

  const setExperience = useCallback((next: DesignExperience) => {
    setExperienceState(next);
    window.localStorage.setItem(DESIGN_EXPERIENCE_STORAGE_KEY, next);
    applyExperience(next);
  }, []);

  const value = useMemo(() => ({ experience, setExperience }), [experience, setExperience]);

  return (
    <DesignExperienceContext.Provider value={value}>
      <div data-design-experience={experience}>{children}</div>
    </DesignExperienceContext.Provider>
  );
}

export function useDesignExperience() {
  const value = useContext(DesignExperienceContext);
  if (!value) throw new Error("useDesignExperience must be used inside DesignExperienceProvider");
  return value;
}
