"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
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

export function DesignExperienceProvider({ children }: { children: ReactNode }) {
  const [experience, setExperienceState] = useState<DesignExperience>(DEFAULT_DESIGN_EXPERIENCE);

  useEffect(() => {
    const stored = window.localStorage.getItem(DESIGN_EXPERIENCE_STORAGE_KEY);
    const next = isDesignExperience(stored) ? stored : DEFAULT_DESIGN_EXPERIENCE;
    setExperienceState(next);
    applyExperience(next);
  }, []);

  const setExperience = useCallback((next: DesignExperience) => {
    setExperienceState(next);
    window.localStorage.setItem(DESIGN_EXPERIENCE_STORAGE_KEY, next);
    applyExperience(next);
  }, []);

  const value = useMemo(() => ({ experience, setExperience }), [experience, setExperience]);

  return <DesignExperienceContext.Provider value={value}>{children}</DesignExperienceContext.Provider>;
}

export function useDesignExperience() {
  const value = useContext(DesignExperienceContext);
  if (!value) throw new Error("useDesignExperience must be used inside DesignExperienceProvider");
  return value;
}
