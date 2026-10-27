/** Own-profile response from GET /api/v1/profile. Identity and internal fields are never part of this shape. */
export type OwnProfile = {
  publicId: string;
  displayName: string;
  bio: string | null;
  profilePrompt: string | null;
  goals: string[];
  interests: string[];
  discoverable: boolean;
};

/** Privacy-safe card returned by GET /api/v1/discovery. Viewer like state is not included. */
export type DiscoveryCard = {
  publicId: string;
  displayName: string;
  bio: string | null;
  profilePrompt: string | null;
  goals: string[];
  interests: string[];
};

export type DiscoveryPage = {
  items: DiscoveryCard[];
  nextCursor: string | null;
};

type Json = Record<string, unknown>;

function isObject(value: unknown): value is Json {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function optionalString(value: unknown): string | null | undefined {
  if (value === null || value === undefined) {
    return null;
  }
  return typeof value === "string" ? value : undefined;
}

function stringList(value: unknown): string[] | null {
  if (!Array.isArray(value) || !value.every((item) => typeof item === "string")) {
    return null;
  }
  return value;
}

/** Returns a typed own profile, or null when the payload does not match the expected contract. */
export function parseOwnProfile(value: unknown): OwnProfile | null {
  if (!isObject(value)) {
    return null;
  }
  const { publicId, displayName, discoverable } = value;
  const bio = optionalString(value.bio);
  const profilePrompt = optionalString(value.profilePrompt);
  const goals = stringList(value.goals);
  const interests = stringList(value.interests);
  if (
    typeof publicId !== "string" ||
    typeof displayName !== "string" ||
    typeof discoverable !== "boolean" ||
    bio === undefined ||
    profilePrompt === undefined ||
    goals === null ||
    interests === null
  ) {
    return null;
  }
  return { publicId, displayName, bio, profilePrompt, goals, interests, discoverable };
}

/** Returns a typed discovery page, or null when the payload does not match the expected contract. */
export function parseDiscoveryPage(value: unknown): DiscoveryPage | null {
  if (!isObject(value) || !Array.isArray(value.items)) {
    return null;
  }
  const items: DiscoveryCard[] = [];
  for (const item of value.items) {
    if (!isObject(item) || typeof item.publicId !== "string" || typeof item.displayName !== "string") {
      return null;
    }
    const bio = optionalString(item.bio);
    const profilePrompt = optionalString(item.profilePrompt);
    const goals = stringList(item.goals);
    const interests = stringList(item.interests);
    if (bio === undefined || profilePrompt === undefined || goals === null || interests === null) {
      return null;
    }
    items.push({ publicId: item.publicId, displayName: item.displayName, bio, profilePrompt, goals, interests });
  }
  const nextCursor = optionalString(value.nextCursor);
  if (nextCursor === undefined) {
    return null;
  }
  return { items, nextCursor };
}

/** Returns the liked flag from a like or unlike response, or null when malformed. */
export function parseLikeStatus(value: unknown): boolean | null {
  if (!isObject(value) || typeof value.liked !== "boolean") {
    return null;
  }
  return value.liked;
}
