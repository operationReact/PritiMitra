import {
  CognitoIdentityProviderClient,
  ConfirmForgotPasswordCommand,
  ConfirmSignUpCommand,
  ForgotPasswordCommand,
  GetTokensFromRefreshTokenCommand,
  GlobalSignOutCommand,
  InitiateAuthCommand,
  ResendConfirmationCodeCommand,
  SignUpCommand,
} from "@aws-sdk/client-cognito-identity-provider";
import { readCognitoEnv } from "./config";
import { UNSUPPORTED_CHALLENGE_ERROR } from "./errors";

export type IssuedTokens = {
  accessToken: string;
  refreshToken: string;
  expiresIn?: number;
};

let cachedClient: { region: string; client: CognitoIdentityProviderClient } | undefined;

function getClient(region: string): CognitoIdentityProviderClient {
  if (!cachedClient || cachedClient.region !== region) {
    cachedClient = { region, client: new CognitoIdentityProviderClient({ region }) };
  }
  return cachedClient.client;
}

function namedError(name: string, message: string): Error {
  const error = new Error(message);
  error.name = name;
  return error;
}

export async function signUp(email: string, password: string): Promise<void> {
  const env = readCognitoEnv();
  await getClient(env.region).send(
    new SignUpCommand({
      ClientId: env.clientId,
      Username: email,
      Password: password,
      UserAttributes: [{ Name: "email", Value: email }],
    }),
  );
}

export async function confirmSignUp(email: string, code: string): Promise<void> {
  const env = readCognitoEnv();
  await getClient(env.region).send(
    new ConfirmSignUpCommand({ ClientId: env.clientId, Username: email, ConfirmationCode: code }),
  );
}

export async function resendConfirmationCode(email: string): Promise<void> {
  const env = readCognitoEnv();
  await getClient(env.region).send(
    new ResendConfirmationCodeCommand({ ClientId: env.clientId, Username: email }),
  );
}

export async function signIn(email: string, password: string): Promise<IssuedTokens> {
  // USER_PASSWORD_AUTH is enabled only on the web app client (infra/terraform/cognito.tf).
  const env = readCognitoEnv();
  const output = await getClient(env.region).send(
    new InitiateAuthCommand({
      ClientId: env.clientId,
      AuthFlow: "USER_PASSWORD_AUTH",
      AuthParameters: { USERNAME: email, PASSWORD: password },
    }),
  );
  if (output.ChallengeName) {
    throw namedError(UNSUPPORTED_CHALLENGE_ERROR, "Cognito returned a challenge that is not supported");
  }
  return toIssuedTokens(output.AuthenticationResult);
}

/**
 * Exchanges a refresh token for new tokens. The app client has refresh-token rotation enabled, so the response
 * carries a new refresh token that must replace the old one. Callers must persist it.
 */
export async function refreshTokens(refreshToken: string): Promise<IssuedTokens> {
  const env = readCognitoEnv();
  const output = await getClient(env.region).send(
    new GetTokensFromRefreshTokenCommand({
      ClientId: env.clientId,
      RefreshToken: refreshToken,
    }),
  );
  return toIssuedTokens(output.AuthenticationResult);
}

export async function forgotPassword(email: string): Promise<void> {
  const env = readCognitoEnv();
  await getClient(env.region).send(new ForgotPasswordCommand({ ClientId: env.clientId, Username: email }));
}

export async function confirmForgotPassword(email: string, code: string, password: string): Promise<void> {
  const env = readCognitoEnv();
  await getClient(env.region).send(
    new ConfirmForgotPasswordCommand({
      ClientId: env.clientId,
      Username: email,
      ConfirmationCode: code,
      Password: password,
    }),
  );
}

/** Revokes all refresh tokens for the user that owns this access token. */
export async function globalSignOut(accessToken: string): Promise<void> {
  const env = readCognitoEnv();
  await getClient(env.region).send(new GlobalSignOutCommand({ AccessToken: accessToken }));
}

function toIssuedTokens(result: { AccessToken?: string; RefreshToken?: string; ExpiresIn?: number } | undefined): IssuedTokens {
  if (!result?.AccessToken || !result.RefreshToken) {
    throw namedError("AuthResultMissingError", "Cognito did not return the expected tokens");
  }
  return {
    accessToken: result.AccessToken,
    refreshToken: result.RefreshToken,
    expiresIn: result.ExpiresIn,
  };
}
