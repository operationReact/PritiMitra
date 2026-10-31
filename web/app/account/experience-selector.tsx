"use client";

import { useId, useState } from "react";
import { useDesignExperience } from "@/app/design-experience-provider";
import { DESIGN_EXPERIENCE_META, DESIGN_EXPERIENCES, type DesignExperience } from "@/lib/design/experience";
import { semanticTokensFor } from "@/lib/design/semantic-tokens";

export function ExperienceSelector() {
  const { experience, setExperience } = useDesignExperience();
  const groupId = useId();
  const [announcement, setAnnouncement] = useState<string | null>(null);

  function select(next: DesignExperience) {
    if (next === experience) return;
    setExperience(next);
    setAnnouncement(`${DESIGN_EXPERIENCE_META[next].label} experience applied.`);
  }

  return (
    <section className="experience-selector" aria-labelledby={`${groupId}-title`}>
      <div className="experience-selector-heading">
        <span className="experience-selector-kicker">APPEARANCE</span>
        <h2 id={`${groupId}-title`}>Experience</h2>
        <p>Choose how PritiMitra looks on this device. Your account, privacy settings and in-progress work stay the same.</p>
      </div>

      <div className="experience-options" role="radiogroup" aria-labelledby={`${groupId}-title`}>
        {DESIGN_EXPERIENCES.map((option) => {
          const meta = DESIGN_EXPERIENCE_META[option];
          const tokens = semanticTokensFor(option);
          const checked = option === experience;
          return (
            <label
              key={option}
              className="experience-option"
              data-selected={checked ? "true" : "false"}
              style={{
                background: tokens.colors.surface,
                borderColor: checked ? tokens.colors.accent : tokens.colors.border,
                color: tokens.colors.textPrimary,
              }}
            >
              <input
                type="radio"
                name={`${groupId}-experience`}
                value={option}
                checked={checked}
                onChange={() => select(option)}
              />
              <span className="experience-preview" aria-hidden="true">
                <span style={{ background: tokens.colors.accent }} />
                <span style={{ background: tokens.colors.privacyAccent }} />
                <span style={{ background: tokens.colors.background }} />
              </span>
              <span className="experience-option-text">
                <strong>{meta.label}</strong>
                <span style={{ color: tokens.colors.textMuted }}>{meta.description}</span>
              </span>
            </label>
          );
        })}
      </div>

      <p className="experience-selector-status" role="status" aria-live="polite">{announcement}</p>
    </section>
  );
}
