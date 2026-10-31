"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useSyncExternalStore,
  type ReactNode,
} from "react";
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
const STORAGE_EVENT = "storage";
const LOCAL_CHANGE_EVENT = "pritimitra-design-experience";

function readStoredExperience(): DesignExperience {
  try {
    const stored = window.localStorage.getItem(DESIGN_EXPERIENCE_STORAGE_KEY);
    return isDesignExperience(stored) ? stored : DEFAULT_DESIGN_EXPERIENCE;
  } catch {
    return DEFAULT_DESIGN_EXPERIENCE;
  }
}

function subscribe(onChange: () => void) {
  window.addEventListener(STORAGE_EVENT, onChange);
  window.addEventListener(LOCAL_CHANGE_EVENT, onChange);
  return () => {
    window.removeEventListener(STORAGE_EVENT, onChange);
    window.removeEventListener(LOCAL_CHANGE_EVENT, onChange);
  };
}

// The server snapshot is always the default, so hydration matches server HTML;
// the stored preference is applied on the client without a hydration mismatch.
function getServerExperience(): DesignExperience {
  return DEFAULT_DESIGN_EXPERIENCE;
}

export function DesignExperienceProvider({ children }: { children: ReactNode }) {
  const experience = useSyncExternalStore(subscribe, readStoredExperience, getServerExperience);

  useEffect(() => {
    document.documentElement.dataset.experience = experience;
  }, [experience]);

  const setExperience = useCallback((next: DesignExperience) => {
    try {
      window.localStorage.setItem(DESIGN_EXPERIENCE_STORAGE_KEY, next);
    } catch {
      // Storage unavailable (private mode or quota): keep the in-session choice.
    }
    window.dispatchEvent(new Event(LOCAL_CHANGE_EVENT));
    document.documentElement.dataset.experience = next;
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
