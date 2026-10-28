"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useState, type FormEvent } from "react";

type LegalConfig = {
  termsVersion: string;
  privacyVersion: string;
  termsUrl: string | null;
  privacyUrl: string | null;
  ready: boolean;
};
type OnboardingState = {
  status?: string;
  ageVerified?: boolean;
  requiredConsentsAccepted?: boolean;
  discoveryPreferencesConfigured?: boolean;
};
type Goal = "DATING" | "FRIENDSHIP" | "COMMUNITY" | "MEET_PEOPLE" | "OPEN_TO_CONNECTION";
const GOALS: { value: Goal; title: string; description: string }[] = [
  { value: "DATING", title: "Dating", description: "Explore a potential romantic connection." },
  { value: "FRIENDSHIP", title: "Friendship", description: "Meet people who could become your people." },
  { value: "COMMUNITY", title: "Community", description: "Find belonging and shared experiences." },
  { value: "MEET_PEOPLE", title: "Meet people", description: "Keep it open and see who you connect with." },
  { value: "OPEN_TO_CONNECTION", title: "Open to connection", description: "No pressure to define it right now." },
];

async function readError(response: Response): Promise<string> {
  try {
    const body: unknown = await response.json();
    if (typeof body === "object" && body !== null && "code" in body && (body as { code: unknown }).code === "AGE_INELIGIBLE") {
      return "PritiMitra is for adults aged 18 and over. This account cannot continue onboarding.";
    }
  } catch { /* Use a safe generic message below. */ }
  if (response.status === 401) return "Your session has expired. Please sign in again.";
  if (response.status === 403) return "This action is not available for this account yet.";
  return "We couldn't save that step. Check your connection and try again.";
}

export function OnboardingFlow() {
  const router = useRouter();
  const [step, setStep] = useState<"age" | "consent" | "profile">("age");
  const [legal, setLegal] = useState<LegalConfig | null>(null);
  const [dob, setDob] = useState("");
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [privacyAccepted, setPrivacyAccepted] = useState(false);
  const [displayName, setDisplayName] = useState("");
  const [bio, setBio] = useState("");
  const [profilePrompt, setProfilePrompt] = useState("");
  const [goals, setGoals] = useState<Goal[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [legalResponse, stateResponse] = await Promise.all([
        fetch("/api/public/legal-config", { cache: "no-store" }),
        fetch("/api/backend/v1/onboarding", { cache: "no-store" }),
      ]);
      if (stateResponse.status === 401) { router.replace("/sign-in"); return; }
      if (!stateResponse.ok) throw new Error("We couldn't load your onboarding status.");
      const state = await stateResponse.json() as OnboardingState;
      if (state.status === "ACTIVE") { router.replace("/home"); return; }
      if (state.status === "SUSPENDED" || state.status === "INELIGIBLE") {
        setError("This account cannot continue onboarding. Contact support if you believe this is a mistake.");
      } else if (!state.ageVerified) setStep("age");
      else if (!state.requiredConsentsAccepted) setStep("consent");
      else setStep("profile");

      if (legalResponse.ok) setLegal(await legalResponse.json() as LegalConfig);
      else setLegal(null);
    } catch {
      setError("We couldn't load onboarding. Refresh the page or sign in again.");
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => { void load(); }, [load]);

  async function send(path: string, method: "PUT" | "POST", payload?: unknown): Promise<boolean> {
    const response = await fetch(path, {
      method, cache: "no-store",
      headers: payload === undefined ? undefined : { "Content-Type": "application/json" },
      body: payload === undefined ? undefined : JSON.stringify(payload),
    });
    if (!response.ok) {
      setError(await readError(response));
      return false;
    }
    return true;
  }

  async function submitAge(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true); setError(null); setNotice(null);
    try {
      if (await send("/api/backend/v1/onboarding/age-verification", "PUT", { dateOfBirth: dob })) {
        setStep("consent");
        setNotice("Age eligibility confirmed. Next, review the required policies.");
      }
    } catch { setError("We couldn't reach PritiMitra. Please try again."); }
    finally { setBusy(false); }
  }

  async function submitConsents(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!legal?.ready || !termsAccepted || !privacyAccepted) {
      setError("Approved Terms of Service and Privacy Notice must be configured and accepted before you can continue.");
      return;
    }
    setBusy(true); setError(null); setNotice(null);
    try {
      const ok = await send("/api/backend/v1/onboarding/consents", "PUT", {
        consents: [
          { type: "TERMS_OF_SERVICE", policyVersion: legal.termsVersion, accepted: true },
          { type: "PRIVACY_NOTICE", policyVersion: legal.privacyVersion, accepted: true },
        ],
      });
      if (ok) { setStep("profile"); setNotice("Your required consent records were saved."); }
    } catch { setError("We couldn't reach PritiMitra. Please try again."); }
    finally { setBusy(false); }
  }

  async function submitProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!displayName.trim() || goals.length === 0) {
      setError("Choose a display name and at least one connection goal.");
      return;
    }
    setBusy(true); setError(null); setNotice(null);
    try {
      const saved = await send("/api/backend/v1/onboarding/profile", "PUT", {
        displayName: displayName.trim(),
        bio: bio.trim(),
        profilePrompt: profilePrompt.trim(),
        goals,
        interests: [],
      });
      if (!saved) return;
      if (await send("/api/backend/v1/onboarding/complete", "POST")) {
        router.replace("/home");
      }
    } catch { setError("We couldn't reach PritiMitra. Please try again."); }
    finally { setBusy(false); }
  }

  const maxDob = new Date();
  maxDob.setFullYear(maxDob.getFullYear() - 18);
  const maxDobText = [maxDob.getFullYear(), String(maxDob.getMonth() + 1).padStart(2, "0"), String(maxDob.getDate()).padStart(2, "0")].join("-");

  return (
    <main className="onboarding-page">
      <section className="onboarding-card" aria-labelledby="onboarding-title">
        <Link href="/" className="onboarding-back">PritiMitra <span aria-hidden="true">✦</span></Link>
        <div className="onboarding-progress" aria-label={"Step " + (step === "age" ? 1 : step === "consent" ? 2 : 3) + " of 3"}>
          {(["age", "consent", "profile"] as const).map((item, index) => <span key={item} className={item === step ? "is-current" : (["age", "consent", "profile"].indexOf(step) > index ? "is-complete" : "")} />)}
        </div>
        {loading ? <p role="status">Loading your setup…</p> : <>
          <p className="onboarding-eyebrow">YOUR SPACE, YOUR PACE</p>
          <h1 id="onboarding-title">{step === "age" ? "First, a quick age check." : step === "consent" ? "Connection starts with clarity." : "Make your profile feel like you."}</h1>
          <p className="onboarding-description">{step === "age" ? "PritiMitra is designed for adults aged 18 and over. Your date of birth is used for eligibility and is not shown on your public profile." : step === "consent" ? "Review the current Terms of Service and Privacy Notice before continuing." : "Tell people a little about you and what kind of connection you’re open to. You can refine your profile later."}</p>
          {error ? <p className="auth-error" role="alert">{error}</p> : null}
          {notice ? <p className="auth-notice" role="status">{notice}</p> : null}
          {step === "age" ? <form className="auth-form" onSubmit={submitAge}>
            <label><span>Date of birth</span><input type="date" value={dob} max={maxDobText} onChange={(event) => setDob(event.target.value)} required /></label>
            <button className="auth-submit" type="submit" disabled={busy || !dob}>{busy ? "Saving…" : "Continue"}<span aria-hidden="true">↗</span></button>
          </form> : null}
          {step === "consent" ? <form className="auth-form" onSubmit={submitConsents}>
            {legal?.ready && legal.termsUrl && legal.privacyUrl ? <div className="legal-links">
              <a href={legal.termsUrl} target="_blank" rel="noreferrer">Read Terms of Service ↗</a>
              <a href={legal.privacyUrl} target="_blank" rel="noreferrer">Read Privacy Notice ↗</a>
            </div> : <div className="legal-blocked" role="alert">
              <strong>Onboarding is paused until policy configuration is complete.</strong>
              <p>Approved, versioned Terms and Privacy URLs are not configured for this environment. Draft policies cannot be accepted as production approval.</p>
            </div>}
            <label className="auth-check"><input type="checkbox" checked={termsAccepted} onChange={(event) => setTermsAccepted(event.target.checked)} disabled={!legal?.ready} /><span>I agree to the current Terms of Service.</span></label>
            <label className="auth-check"><input type="checkbox" checked={privacyAccepted} onChange={(event) => setPrivacyAccepted(event.target.checked)} disabled={!legal?.ready} /><span>I acknowledge the current Privacy Notice.</span></label>
            <button className="auth-submit" type="submit" disabled={busy || !legal?.ready || !termsAccepted || !privacyAccepted}>{busy ? "Saving…" : "Accept and continue"}<span aria-hidden="true">↗</span></button>
          </form> : null}
          {step === "profile" ? <form className="auth-form" onSubmit={submitProfile}>
            <label><span>Display name</span><input type="text" value={displayName} maxLength={60} onChange={(event) => setDisplayName(event.target.value)} autoComplete="nickname" required /></label>
            <label><span>A little about you <small>(optional)</small></span><textarea value={bio} maxLength={500} rows={3} onChange={(event) => setBio(event.target.value)} /></label>
            <label><span>A conversation starter <small>(optional)</small></span><textarea value={profilePrompt} maxLength={200} rows={2} onChange={(event) => setProfilePrompt(event.target.value)} placeholder="Something I could talk about for hours…" /></label>
            <fieldset className="goal-fieldset"><legend>What kind of connection are you looking for?</legend>
              {GOALS.map((goal) => <label className="goal-option" key={goal.value}>
                <input type="checkbox" checked={goals.includes(goal.value)} onChange={(event) => setGoals((current) => event.target.checked ? [...current, goal.value] : current.filter((item) => item !== goal.value))} />
                <span><strong>{goal.title}</strong><small>{goal.description}</small></span>
              </label>)}
            </fieldset>
            <button className="auth-submit" type="submit" disabled={busy || !displayName.trim() || goals.length === 0}>{busy ? "Finishing setup…" : "Finish setup"}<span aria-hidden="true">↗</span></button>
          </form> : null}
        </>}
        <p className="onboarding-footer">You can edit your profile and privacy settings later. <Link href="/account">Account help</Link></p>
      </section>
    </main>
  );
}
