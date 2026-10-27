"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { destinationForAccountStatus } from "@/lib/auth/redirect";

export type AuthMode = "sign-in" | "sign-up" | "confirm" | "forgot" | "reset";

const COPY: Record<AuthMode, { eyebrow: string; title: string; description: string; submit: string }> = {
  "sign-in": { eyebrow: "WELCOME BACK", title: "Your space is waiting.", description: "Sign in to pick up where your story left off.", submit: "Sign in" },
  "sign-up": { eyebrow: "START WITH YOU", title: "Make room for connection.", description: "Create an account for dating, friendship, and community on your terms.", submit: "Create account" },
  confirm: { eyebrow: "ONE SMALL STEP", title: "Verify your email.", description: "Enter the six-digit code we sent to your email address.", submit: "Verify email" },
  forgot: { eyebrow: "ACCOUNT RECOVERY", title: "Let's get you back in.", description: "We’ll send a recovery code if the account can receive one.", submit: "Send recovery code" },
  reset: { eyebrow: "ACCOUNT RECOVERY", title: "Choose a new password.", description: "Enter the code from your email and choose a new password.", submit: "Reset password" },
};

const ERROR_COPY: Record<string, string> = {
  invalid_input: "Check the details and try again.",
  invalid_password: "Choose a password that meets the account password policy.",
  invalid_code: "That code doesn’t look right. Check it and try again.",
  code_expired: "That code has expired. Request a new one and try again.",
  invalid_credentials: "The email or password is incorrect.",
  email_not_verified: "Verify your email first. Enter your code below or request another one.",
  password_reset_required: "This account needs a password reset. Use “Forgot password” to continue.",
  challenge_not_supported: "This sign-in method is not supported yet. Use password recovery or contact support.",
  rate_limited: "Too many attempts. Please wait a little before trying again.",
  csrf_rejected: "Your request could not be verified. Reload this page and try again.",
  auth_unavailable: "Authentication is temporarily unavailable. Please try again shortly.",
  invalid_request: "The request could not be processed. Check the details and try again.",
};

async function responseError(response: Response): Promise<string> {
  try {
    const body: unknown = await response.json();
    if (typeof body === "object" && body !== null && "error" in body) {
      const code = String((body as { error: unknown }).error);
      return ERROR_COPY[code] ?? "We couldn't complete that request. Please try again.";
    }
  } catch {
    // Never display raw server responses or infrastructure details.
  }
  return "We couldn't complete that request. Please try again.";
}

export function AuthFlow({ mode }: { mode: AuthMode }) {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    const query = new URLSearchParams(window.location.search);
    setEmail(query.get("email") ?? "");
    if (query.get("confirmed") === "1") setNotice("Your email is confirmed. Sign in to continue.");
    if (query.get("reset") === "1") setNotice("Your password has been reset. Sign in with the new password.");
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setError(null);
    setNotice(null);
    if ((mode === "sign-up" || mode === "reset") && password !== confirmPassword) {
      setError("Your passwords do not match.");
      return;
    }

    setBusy(true);
    try {
      if (mode === "sign-in") {
        const response = await fetch("/api/auth/sign-in", {
          method: "POST", cache: "no-store", headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email, password }),
        });
        if (!response.ok) {
          const message = await responseError(response);
          if (message === ERROR_COPY.email_not_verified) router.push("/confirm-sign-up?email=" + encodeURIComponent(email));
          else setError(message);
          return;
        }
        const me = await fetch("/api/backend/v1/me", { cache: "no-store" });
        if (me.ok) {
          const body: unknown = await me.json();
          const status = typeof body === "object" && body !== null && "status" in body
            ? String((body as { status: unknown }).status)
            : "";
          const destination = destinationForAccountStatus(status);
          router.replace(destination === "/confirm-sign-up"
            ? destination + "?email=" + encodeURIComponent(email)
            : destination);
          return;
        }
        router.replace("/account");
        return;
      }

      const requests: Record<Exclude<AuthMode, "sign-in">, { url: string; payload: Record<string, string> }> = {
        "sign-up": { url: "/api/auth/sign-up", payload: { email, password } },
        confirm: { url: "/api/auth/confirm", payload: { email, code } },
        forgot: { url: "/api/auth/forgot-password", payload: { email } },
        reset: { url: "/api/auth/reset-password", payload: { email, code, password } },
      };
      const request = requests[mode as Exclude<AuthMode, "sign-in">];
      const response = await fetch(request.url, {
        method: "POST", cache: "no-store", headers: { "Content-Type": "application/json" },
        body: JSON.stringify(request.payload),
      });
      if (!response.ok) {
        setError(await responseError(response));
        return;
      }
      if (mode === "sign-up") router.push("/confirm-sign-up?email=" + encodeURIComponent(email));
      else if (mode === "confirm") router.push("/sign-in?confirmed=1");
      else if (mode === "forgot") router.push("/reset-password?email=" + encodeURIComponent(email));
      else router.push("/sign-in?reset=1");
    } catch {
      setError("We couldn't reach PritiMitra. Check your connection and try again.");
    } finally {
      setBusy(false);
    }
  }

  async function resendCode() {
    if (busy || !email.trim()) {
      setError("Enter your email address first.");
      return;
    }
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const response = await fetch("/api/auth/resend-code", {
        method: "POST", cache: "no-store", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email }),
      });
      if (!response.ok) setError(await responseError(response));
      else setNotice("If the account can receive a code, a new code has been requested.");
    } catch {
      setError("We couldn't reach PritiMitra. Check your connection and try again.");
    } finally {
      setBusy(false);
    }
  }

  const copy = COPY[mode];
  const showPassword = mode === "sign-in" || mode === "sign-up" || mode === "reset";
  const showCode = mode === "confirm" || mode === "reset";

  return (
    <main className="auth-page">
      <div className="auth-glow auth-glow-one" aria-hidden="true" />
      <div className="auth-glow auth-glow-two" aria-hidden="true" />
      <section className="auth-card" aria-labelledby="auth-title">
        <Link className="auth-brand" href="/" aria-label="PritiMitra home">
          <Image src="/brand/image_logo.png" width={37} height={34} alt="" priority />
          <Image src="/brand/wordmark.png" width={112} height={38} alt="PritiMitra" priority />
        </Link>
        <div className="auth-intro">
          <span className="auth-eyebrow"><span aria-hidden="true">✦</span> {copy.eyebrow}</span>
          <h1 id="auth-title">{copy.title}</h1>
          <p>{copy.description}</p>
        </div>
        {notice ? <p className="auth-notice" role="status">{notice}</p> : null}
        {error ? <p className="auth-error" role="alert">{error}</p> : null}
        <form className="auth-form" onSubmit={submit} noValidate={false}>
          <label>
            <span>Email address</span>
            <input type="email" name="email" autoComplete="email" inputMode="email" maxLength={254}
              value={email} onChange={(event) => setEmail(event.target.value)} required />
          </label>
          {showCode ? <label>
            <span>Six-digit code</span>
            <input type="text" name="code" autoComplete="one-time-code" inputMode="numeric" pattern="[0-9]{6}" minLength={6} maxLength={6}
              value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, "").slice(0, 6))} required />
          </label> : null}
          {showPassword ? <label>
            <span>{mode === "reset" ? "New password" : "Password"}</span>
            <input type="password" name="password" autoComplete={mode === "sign-in" ? "current-password" : "new-password"}
              maxLength={256} value={password} onChange={(event) => setPassword(event.target.value)} required />
          </label> : null}
          {(mode === "sign-up" || mode === "reset") ? <label>
            <span>Confirm password</span>
            <input type="password" name="confirmPassword" autoComplete="new-password" maxLength={256}
              value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} required />
          </label> : null}
          {mode === "sign-up" ? <p className="auth-field-note">You’ll verify your email before accessing the community.</p> : null}
          {mode === "sign-in" ? <div className="auth-inline-link"><Link href="/forgot-password">Forgot password?</Link></div> : null}
          <button className="auth-submit" type="submit" disabled={busy}>
            {busy ? "Please wait…" : copy.submit}<span aria-hidden="true">↗</span>
          </button>
        </form>
        {mode === "confirm" ? <button className="auth-text-button" type="button" onClick={() => void resendCode()} disabled={busy}>Resend confirmation code</button> : null}
        <div className="auth-footnote">
          {mode === "sign-in" ? <>New to PritiMitra? <Link href="/sign-up">Create an account</Link></> : null}
          {mode === "sign-up" ? <>Already have an account? <Link href="/sign-in">Sign in</Link></> : null}
          {mode === "confirm" ? <>Verified already? <Link href="/sign-in">Sign in</Link></> : null}
          {mode === "forgot" ? <>Remembered it? <Link href="/sign-in">Return to sign in</Link></> : null}
          {mode === "reset" ? <>Need a new code? <Link href="/forgot-password">Request another</Link></> : null}
        </div>
        <p className="auth-privacy">Your identity, your pace. We never put authentication tokens in browser storage.</p>
      </section>
    </main>
  );
}
