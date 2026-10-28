"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";

export function AccountControls() {
  const router = useRouter();
  const [confirming, setConfirming] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [communityNicknameAccepted, setCommunityNicknameAccepted] = useState<boolean | null>(null);
  const [consentBusy, setConsentBusy] = useState(false);
  const [consentMessage, setConsentMessage] = useState<string | null>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    let active = true;
    void fetch("/api/backend/v1/account/consents", { cache: "no-store" })
      .then(async (response) => {
        if (!response.ok) throw new Error("unavailable");
        const body: unknown = await response.json();
        if (typeof body !== "object" || body === null || !("communityNicknameAccepted" in body)
          || typeof (body as { communityNicknameAccepted: unknown }).communityNicknameAccepted !== "boolean") {
          throw new Error("invalid_response");
        }
        if (active) setCommunityNicknameAccepted((body as { communityNicknameAccepted: boolean }).communityNicknameAccepted);
      })
      .catch(() => {
        if (active) {
          setCommunityNicknameAccepted(false);
          setConsentMessage("Optional consent settings are temporarily unavailable.");
        }
      });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (confirming) cancelRef.current?.focus();
  }, [confirming]);

  async function updateCommunityNicknameConsent(accepted: boolean) {
    if (consentBusy) return;
    setConsentBusy(true);
    setConsentMessage(null);
    try {
      const response = await fetch("/api/backend/v1/account/consents/community-nickname", {
        method: "PUT",
        cache: "no-store",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ accepted }),
      });
      if (!response.ok) {
        setConsentMessage("We couldn't save this preference. Please try again.");
        return;
      }
      const body: unknown = await response.json();
      if (typeof body !== "object" || body === null || !("communityNicknameAccepted" in body)
        || typeof (body as { communityNicknameAccepted: unknown }).communityNicknameAccepted !== "boolean") {
        setConsentMessage("We couldn't confirm this preference was saved.");
        return;
      }
      setCommunityNicknameAccepted((body as { communityNicknameAccepted: boolean }).communityNicknameAccepted);
      setConsentMessage("Your optional consent preference has been saved.");
    } catch {
      setConsentMessage("We couldn't reach PritiMitra. Please check your connection and try again.");
    } finally {
      setConsentBusy(false);
    }
  }

  async function requestDeletion() {
    if (deleting) return;
    setDeleting(true);
    setError(null);
    try {
      const response = await fetch("/api/backend/v1/account", {
        method: "DELETE",
        cache: "no-store",
        headers: { "Content-Type": "application/json" },
      });
      if (response.status === 202 || response.status === 204) {
        router.replace("/sign-in?deleted=1");
        return;
      }
      if (response.status === 401) {
        router.replace("/sign-in");
        return;
      }
      setError("We couldn't start account deletion. Your account has not been deleted. Please try again.");
    } catch {
      setError("We couldn't reach PritiMitra. Your deletion request was not confirmed; check your connection and try again.");
    } finally {
      setDeleting(false);
    }
  }

  return (
    <section className="account-controls" aria-labelledby="account-controls-title">
      <div className="account-controls-heading">
        <span className="account-controls-kicker">ACCOUNT & PRIVACY</span>
        <h2 id="account-controls-title">You’re in control.</h2>
        <p>Manage your account with clear choices and no hidden steps.</p>
      </div>

      <div className="account-control-row">
        <div>
          <h3>Required legal consent</h3>
          <p>Terms of Service and Privacy Notice consent are required while your account is active. To withdraw that required consent, you can request account deletion. Your consent records and personal account data are removed as part of the deletion workflow.</p>
        </div>
      </div>

      <div className="account-control-row account-consent-row">
        <div>
          <h3>Community nickname consent <span className="account-optional-label">OPTIONAL</span></h3>
          <p>This preference will govern use of a community nickname when that feature is enabled. The feature is not active yet; changing this setting records your choice for future use. It does not affect required legal consent or account access.</p>
          {consentMessage ? <p className="account-consent-message" role="status">{consentMessage}</p> : null}
        </div>
        <label className="account-consent-toggle">
          <input
            type="checkbox"
            checked={communityNicknameAccepted ?? false}
            disabled={consentBusy || communityNicknameAccepted === null}
            onChange={(event) => void updateCommunityNicknameConsent(event.target.checked)}
          />
          <span>{consentBusy ? "Saving…" : communityNicknameAccepted ? "Allowed" : "Not allowed"}</span>
        </label>
      </div>

      <div className="account-delete-panel">
        <div>
          <span className="account-delete-label">IRREVERSIBLE ACTION</span>
          <h3>Delete your account</h3>
          <p>This signs you out immediately and queues removal of your PritiMitra account, profile data, consent records, and stored profile photos. Community posts are anonymized and removed from feeds. Cleanup can take a short time to finish; failures are retried securely.</p>
        </div>
        <button className="account-delete-button" type="button" onClick={() => { setError(null); setConfirming(true); }}>
          Delete account
        </button>
      </div>

      {error ? <p className="account-controls-error" role="alert">{error}</p> : null}

      {confirming ? (
        <div className="account-delete-overlay">
          <section className="account-delete-dialog" role="alertdialog" aria-modal="true" aria-labelledby="delete-dialog-title" aria-describedby="delete-dialog-description">
            <span className="account-delete-label">PLEASE CONFIRM</span>
            <h2 id="delete-dialog-title">Delete your account?</h2>
            <p id="delete-dialog-description">This action cannot be undone. You’ll be signed out as soon as the request is accepted. Account removal is processed by a retryable cleanup workflow.</p>
            {error ? <p className="account-controls-error" role="alert">{error}</p> : null}
            <div className="account-delete-actions">
              <button ref={cancelRef} className="account-cancel-button" type="button" disabled={deleting} onClick={() => setConfirming(false)}>
                Keep my account
              </button>
              <button className="account-delete-button" type="button" disabled={deleting} onClick={() => void requestDeletion()}>
                {deleting ? "Submitting…" : "Confirm deletion"}
              </button>
            </div>
          </section>
        </div>
      ) : null}
    </section>
  );
}
