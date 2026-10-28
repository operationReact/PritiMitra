# Phase 7 — Trust and safety incident-response runbook

Status: implementation runbook draft. The operator, escalation contacts, response targets, retention schedule, and user-facing wording must be approved before production operations begin. This document does not create moderator access or configure AWS resources.

## 1. Scope and operating principles

This runbook covers user reports, urgent safety concerns, moderator decisions, account suspension, suspected platform abuse, security/privacy incidents, and user-requested account deletion.

- Protect the affected person first. Do not ask users to investigate, confront another user, or publish identifying evidence.
- Use least-privilege moderator accounts. Only a verified Cognito token carrying the exact `PritiMitraModerators` group is authorized for moderation APIs.
- Record decisions through the audited moderation workflow. Do not edit or delete the append-only safety audit trail to correct a mistake; record a new, attributable corrective action.
- Collect the minimum evidence needed. Never copy passwords, access/refresh tokens, verification codes, full message bodies, or unnecessary identity information into tickets, chat, or audit notes.
- Keep production, staging, and development incidents separate. Do not run destructive tests against real users or production data.
- Treat all response targets below as proposed operational targets until an accountable owner approves them.

## 2. Severity and initial response

| Level | Example | Proposed initial target | Required first actions |
| --- | --- | --- | --- |
| S0 — Immediate danger | Credible imminent threat of physical harm, exploitation, or imminent self-harm | Page the designated safety lead immediately; begin triage without delay | Preserve the minimum relevant report reference, restrict dangerous content/account where justified, escalate to the designated safety lead, and direct the reporter to appropriate local emergency services when relevant |
| S1 — High risk | Credible threats, stalking, targeted harassment, suspected impersonation causing immediate harm, or exposure of sensitive information | Triage within 1 hour during staffed coverage | Assign an incident owner, review report evidence, decide on proportionate restrictions, and notify the safety lead |
| S2 — Standard report | Harassment, unwanted content, spam, or a non-urgent policy concern | Triage within 1 business day | Check for duplicate reports, review relevant evidence, and record a decision or reason for pending review |
| S3 — Product/support issue | False positive, account-access question, or non-safety product issue | Triage within 3 business days | Route to product/support without disclosing private report details |

These targets require staffing and on-call coverage to be meaningful. Do not advertise them as a user-facing SLA until approved and operationally supported.

## 3. Report triage and moderation decisions

1. Open the private moderation queue and verify the report's status, target, and evidence relevant to the report.
2. Check for an existing report or related incident so that duplicate submissions are handled consistently. Do not infer that a user is malicious merely because reports are duplicated.
3. Assess the content and context against the approved policy. Avoid decisions based on a user's sexual orientation, gender identity, disability, ethnicity, or other protected characteristic.
4. Choose only a supported, proportionate action: dismiss an unsupported report; hide a reported community post; hide a profile from discovery; or suspend an account when evidence and policy justify it. Suspension requires a specific, non-empty moderator note.
5. Record the decision through the backend endpoint. Verify the response before telling anyone that an action completed. Reports can be decided once; a correction requires a new audited action or an approved administrative process.
6. For a complex or disputed case, leave it pending and escalate rather than making an irreversible high-impact decision without adequate evidence.

Do not place names, emails, full message text, phone numbers, or other sensitive personal data in moderator notes. Use concise, factual, policy-relevant wording.

## 4. Security, privacy, and data incidents

Escalate suspected account takeover, unauthorized moderator access, token exposure, cross-user data exposure, deletion failures affecting personal data, or unexpected public access to private photos as a security/privacy incident.

1. Notify the designated security/privacy owner through the approved internal channel; do not use an affected customer's account to investigate.
2. Record the incident ID, discovery time, affected subsystem, safe references, containment steps, and who made each decision. Keep secrets and personal payloads out of incident notes.
3. Contain access using approved controls, such as revoking affected sessions or removing an incorrectly assigned moderator group. Never add ordinary users to the moderator group as a workaround.
4. Preserve relevant logs and append-only audit records under the approved retention/access policy. Restrict access to people handling the incident.
5. Assess the data and users potentially affected, document uncertainty, and involve the privacy/legal owner to determine any notification obligations and timelines.
6. Verify recovery with focused negative tests and an independent reviewer. Close only when containment, impact assessment, remediation, and any required follow-up are recorded.

Do not perform live Cognito, S3, DNS, or production data actions as part of an investigation unless the applicable environment owner has approved the specific action.

## 5. Account deletion and consent withdrawal

- A successful `DELETE /api/v1/account` response of `202 Accepted` means deletion was queued, not that every external object has already been removed. Do not tell the user that cleanup is complete until the worker marks the request complete.
- Failed Cognito or private-photo cleanup must remain retryable. Escalate requests that remain pending beyond the approved operational threshold; do not manually delete database rows to make the queue appear empty.
- After deletion, the application retains only the designed privacy-safe deletion tombstone and lifecycle audit metadata. Access to this metadata remains restricted.
- Required Terms of Service and Privacy Notice acceptance is not an optional feature toggle. A user who no longer wants an account may request account deletion. Optional consent withdrawal must record a new history event rather than mutate prior consent history.
- Do not promise deletion from legally required incident evidence or third-party systems beyond what the approved retention and privacy policy supports.

## 6. Communication and escalation

Use the approved response templates once product, safety, and privacy owners approve them. A response should acknowledge receipt, explain the current status without exposing another user's information, and avoid promises the system cannot verify.

Escalate to:
- **Safety lead:** imminent threats, repeated targeted abuse, difficult suspension decisions, or an appeal involving a high-impact action.
- **Security owner:** suspected compromise, authorization bypass, token leakage, or cross-user access.
- **Privacy/legal owner:** potential personal-data exposure, deletion/retention questions, requests from authorities, or notification decisions.
- **Engineering on-call:** API/worker failures, growing deletion backlog, database migration failures, or broken audit/report workflows.
- **Product owner:** policy ambiguity, repeated false positives, or decisions requiring changes to product behavior.

Production operations must populate and test actual contact routes, coverage hours, backup responders, and escalation timeouts. No contact information is embedded here because it must come from the organization's approved operational directory.

## 7. Closure checklist

Before closing an incident, verify that:
- The severity and incident owner are recorded.
- Immediate risk is contained and actions are attributable.
- Evidence is limited to what is needed and stored in an approved location.
- User communications and any privacy/legal assessment are complete or explicitly assigned.
- Corrective actions have owners and due dates.
- Relevant regression/security tests have been added or linked.
- The append-only audit trail remains intact and the incident has been reviewed by a second authorized person for high-impact cases.

## 8. Production readiness gates

Before this runbook is considered operational:
- Name an accountable safety lead, security owner, privacy/legal owner, engineering on-call, and backups.
- Ratify severity definitions, staffing coverage, response targets, evidence retention, user communication templates, and appeal handling.
- Verify staging moderator/non-moderator access, report decisions, audit history and CSV export, account-deletion cleanup/retries, and alerts for a growing deletion backlog.
- Conduct a tabletop exercise for an imminent-risk report and for suspected unauthorized access to private profile data.
- Keep the platform's trust/safety policy and legal/privacy documents aligned with the actual implementation.

No production response SLA, legal approval, moderator staffing, live cloud verification, or deployed infrastructure is implied by this document.
