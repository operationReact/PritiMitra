# Web browser acceptance tests

These tests run against the production-built Next.js app in Chromium at desktop and mobile viewport sizes. They intercept auth/onboarding APIs and use fake test data only; they do not sign in to Cognito, create users, or require AWS credentials.

From the `web/` directory:

```sh
npm ci
npm install --no-save --package-lock=false @playwright/test@1.64.1
npx playwright install chromium
npm run build
npx playwright test
```

The CI workflow installs Playwright separately without adding it to application dependencies or changing `package-lock.json`. A green browser smoke suite is not a substitute for staging Cognito integration, physical-device testing, or comparison with approved Figma frames.
