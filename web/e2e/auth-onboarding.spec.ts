import { expect, test } from "@playwright/test";

const TEST_EMAIL = "member@example.test";

test("sign-in renders responsively and maps invalid credentials to safe copy", async ({ page }) => {
  let submittedPayload: unknown;
  await page.route("**/api/auth/sign-in", async (route) => {
    submittedPayload = route.request().postDataJSON();
    await route.fulfill({
      status: 401,
      contentType: "application/json",
      body: JSON.stringify({ error: "invalid_credentials" }),
    });
  });

  await page.goto("/sign-in");
  await expect(page.getByRole("heading", { name: "Your space is waiting." })).toBeVisible();
  await expect(page.getByLabel("Email address")).toBeVisible();
  await expect(page.getByLabel("Password")).toBeVisible();
  await expect(page.getByRole("link", { name: "Create an account" })).toBeVisible();

  const hasHorizontalOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth > window.innerWidth + 1,
  );
  expect(hasHorizontalOverflow).toBe(false);

  await page.getByLabel("Email address").fill(TEST_EMAIL);
  await page.getByLabel("Password").fill("NotThePassword123!");
  await page.getByRole("button", { name: "Sign in" }).click();

  await expect(page.locator("p.auth-error[role=\"alert\"]")).toHaveText("The email or password is incorrect.");
  expect(submittedPayload).toEqual({ email: TEST_EMAIL, password: "NotThePassword123!" });
  await expect(page).toHaveURL(/\/sign-in$/);
});

test("sign-up catches mismatched passwords before making an API request", async ({ page }) => {
  let requestCount = 0;
  await page.route("**/api/auth/sign-up", async (route) => {
    requestCount += 1;
    await route.fulfill({ status: 201, contentType: "application/json", body: "{}" });
  });

  await page.goto("/sign-up");
  await expect(page.getByRole("heading", { name: "Make room for connection." })).toBeVisible();
  await page.getByLabel("Email address").fill(TEST_EMAIL);
  await page.locator('input[name="password"]').fill("ValidPassword123!");
  await page.getByLabel("Confirm password").fill("DifferentPassword123!");
  await page.getByRole("button", { name: "Create account" }).click();

  await expect(page.locator("p.auth-error[role=\"alert\"]")).toHaveText("Your passwords do not match.");
  expect(requestCount).toBe(0);
});

test("confirmation code input keeps only digits", async ({ page }) => {
  await page.goto("/confirm-sign-up?email=" + encodeURIComponent(TEST_EMAIL));
  await expect(page.getByRole("heading", { name: "Verify your email." })).toBeVisible();
  await expect(page.getByLabel("Email address")).toHaveValue(TEST_EMAIL);

  const code = page.getByLabel("Six-digit code");
  await code.fill("12a34bc");
  await expect(code).toHaveValue("1234");
});

test("unauthenticated onboarding redirects to sign-in", async ({ page }) => {
  await page.route("**/api/public/legal-config", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        termsVersion: "terms-draft-v1",
        privacyVersion: "privacy-draft-v1",
        termsUrl: null,
        privacyUrl: null,
        ready: false,
      }),
    });
  });
  await page.route("**/api/backend/v1/onboarding", async (route) => {
    await route.fulfill({ status: 401, contentType: "application/json", body: "{}" });
  });

  await page.goto("/onboarding");
  await expect(page).toHaveURL(/\/sign-in$/);
  await expect(page.getByRole("heading", { name: "Your space is waiting." })).toBeVisible();
});

test("onboarding fails closed when only draft legal policies are configured", async ({ page }) => {
  let consentWriteCount = 0;
  await page.route("**/api/public/legal-config", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        termsVersion: "terms-draft-v1",
        privacyVersion: "privacy-draft-v1",
        termsUrl: null,
        privacyUrl: null,
        ready: false,
      }),
    });
  });
  await page.route("**/api/backend/v1/onboarding", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        status: "ONBOARDING_INCOMPLETE",
        ageVerified: true,
        requiredConsentsAccepted: false,
        discoveryPreferencesConfigured: false,
      }),
    });
  });
  await page.route("**/api/backend/v1/onboarding/consents", async (route) => {
    consentWriteCount += 1;
    await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
  });

  await page.goto("/onboarding");
  await expect(page.getByRole("heading", { name: "Connection starts with clarity." })).toBeVisible();
  await expect(page.getByText("Onboarding is paused until policy configuration is complete.")).toBeVisible();
  await expect(page.getByRole("checkbox", { name: /current Terms of Service/i })).toBeDisabled();
  await expect(page.getByRole("checkbox", { name: /current Privacy Notice/i })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Accept and continue" })).toBeDisabled();

  const hasHorizontalOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth > window.innerWidth + 1,
  );
  expect(hasHorizontalOverflow).toBe(false);
  expect(consentWriteCount).toBe(0);
});


test("public landing keeps its app-release message clear and interactive", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: /Your people\./ })).toBeVisible();
  await expect(page.getByText("COMING SOON", { exact: false }).first()).toBeVisible();
  await expect(page.getByRole("button", { name: "Download the app" })).toBeVisible();

  if ((page.viewportSize()?.width ?? 1440) < 600) {
    const menu = page.getByRole("button", { name: "Open navigation menu" });
    await expect(menu).toBeVisible();
    await menu.click();
    await expect(page.getByRole("button", { name: "Close navigation menu" })).toHaveAttribute("aria-expanded", "true");
    await expect(page.getByRole("navigation", { name: "Main navigation" }).getByRole("link", { name: "Our mission" })).toBeVisible();
  } else {
    await expect(page.getByRole("navigation", { name: "Main navigation" }).getByRole("link", { name: "Our mission" })).toBeVisible();
  }

  await page.getByRole("button", { name: "Like this profile" }).click();
  await expect(page.getByRole("status")).toContainText("Every connection starts with consent and a hello.");
  const hasHorizontalOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth > window.innerWidth + 1,
  );
  expect(hasHorizontalOverflow).toBe(false);
});

test("signed-out visitors see an account gate instead of fabricated dashboard data", async ({ page }) => {
  await page.goto("/home");
  await expect(page.getByRole("heading", { name: "Let’s get you settled in." })).toBeVisible();
  await expect(page.getByRole("link", { name: /Check account status/ })).toHaveAttribute("href", "/account");
  await expect(page.getByText("—", { exact: true })).toHaveCount(0);
});
