export class AuthConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "AuthConfigError";
  }
}

export type CognitoEnv = {
  region: string;
  clientId: string;
};

type EnvSource = Record<string, string | undefined>;

export function isProductionRuntime(env: EnvSource = process.env): boolean {
  return env.NODE_ENV === "production";
}

export function readCognitoEnv(env: EnvSource = process.env): CognitoEnv {
  const region = env.COGNITO_REGION?.trim();
  const clientId = env.COGNITO_CLIENT_ID?.trim();
  if (!region || !clientId) {
    throw new AuthConfigError("COGNITO_REGION and COGNITO_CLIENT_ID must be configured");
  }
  return { region, clientId };
}

/** Returns the backend origin. Plain HTTP is only accepted outside production. */
export function readBackendOrigin(env: EnvSource = process.env): string {
  const raw = env.BACKEND_API_BASE_URL?.trim();
  if (!raw) {
    throw new AuthConfigError("BACKEND_API_BASE_URL must be configured");
  }
  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    throw new AuthConfigError("BACKEND_API_BASE_URL must be an absolute URL");
  }
  const httpsRequired = isProductionRuntime(env);
  if (url.protocol !== "https:" && (httpsRequired || url.protocol !== "http:")) {
    throw new AuthConfigError("BACKEND_API_BASE_URL must use https in production");
  }
  return url.origin;
}
