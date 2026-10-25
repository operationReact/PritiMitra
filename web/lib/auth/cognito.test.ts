import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const sent = vi.hoisted(() => ({
  send: vi.fn(),
  commands: [] as Array<{ name: string; input: Record<string, unknown> }>,
}));

vi.mock("@aws-sdk/client-cognito-identity-provider", () => {
  class CognitoIdentityProviderClient {
    send = sent.send;
  }
  const command = (name: string) =>
    class {
      input: Record<string, unknown>;
      constructor(input: Record<string, unknown>) {
        this.input = input;
        sent.commands.push({ name, input });
      }
    };
  return {
    CognitoIdentityProviderClient,
    ConfirmForgotPasswordCommand: command("ConfirmForgotPassword"),
    ConfirmSignUpCommand: command("ConfirmSignUp"),
    ForgotPasswordCommand: command("ForgotPassword"),
    GetTokensFromRefreshTokenCommand: command("GetTokensFromRefreshToken"),
    GlobalSignOutCommand: command("GlobalSignOut"),
    InitiateAuthCommand: command("InitiateAuth"),
    ResendConfirmationCodeCommand: command("ResendConfirmationCode"),
    SignUpCommand: command("SignUp"),
  };
});

import { refreshTokens, signIn } from "./cognito";

const ENV_KEYS = ["COGNITO_REGION", "COGNITO_CLIENT_ID"] as const;

describe("cognito adapter", () => {
  beforeEach(() => {
    process.env.COGNITO_REGION = "ap-south-1";
    process.env.COGNITO_CLIENT_ID = "web-client-id";
    sent.send.mockReset();
    sent.commands.length = 0;
  });

  afterEach(() => {
    for (const key of ENV_KEYS) {
      delete process.env[key];
    }
  });

  describe("signIn", () => {
    it("uses USER_PASSWORD_AUTH with the configured web client", async () => {
      sent.send.mockResolvedValueOnce({
        AuthenticationResult: { AccessToken: "access-1", RefreshToken: "refresh-1", ExpiresIn: 3600 },
      });

      const tokens = await signIn("person@example.com", "correct-horse");

      expect(sent.commands).toEqual([
        {
          name: "InitiateAuth",
          input: {
            ClientId: "web-client-id",
            AuthFlow: "USER_PASSWORD_AUTH",
            AuthParameters: { USERNAME: "person@example.com", PASSWORD: "correct-horse" },
          },
        },
      ]);
      expect(tokens).toEqual({ accessToken: "access-1", refreshToken: "refresh-1", expiresIn: 3600 });
    });

    it("rejects an unsupported challenge instead of returning partial tokens", async () => {
      sent.send.mockResolvedValueOnce({ ChallengeName: "SOFTWARE_TOKEN_MFA" });

      await expect(signIn("person@example.com", "pw")).rejects.toMatchObject({
        name: "UnsupportedChallengeError",
      });
    });
  });

  describe("refreshTokens", () => {
    it("uses GetTokensFromRefreshToken and never the legacy REFRESH_TOKEN_AUTH flow", async () => {
      sent.send.mockResolvedValueOnce({
        AuthenticationResult: { AccessToken: "access-2", RefreshToken: "refresh-2", ExpiresIn: 3600 },
      });

      const tokens = await refreshTokens("refresh-1");

      expect(sent.commands).toEqual([
        {
          name: "GetTokensFromRefreshToken",
          input: { ClientId: "web-client-id", RefreshToken: "refresh-1" },
        },
      ]);
      expect(tokens.refreshToken).toBe("refresh-2");
      expect(tokens.accessToken).toBe("access-2");
    });

    it("throws when rotation returns no refresh token so the old token is never kept silently", async () => {
      sent.send.mockResolvedValueOnce({ AuthenticationResult: { AccessToken: "access-2" } });

      await expect(refreshTokens("refresh-1")).rejects.toMatchObject({ name: "AuthResultMissingError" });
    });

    it("propagates Cognito rejections so the caller can end the session", async () => {
      const rejection = Object.assign(new Error("Refresh Token has been revoked"), {
        name: "NotAuthorizedException",
      });
      sent.send.mockRejectedValueOnce(rejection);

      await expect(refreshTokens("revoked")).rejects.toBe(rejection);
    });
  });
});
