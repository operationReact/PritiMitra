import { Suspense } from "react";
import { loadProtectedJson } from "@/lib/profile/server";
import { parseOwnProfile } from "@/lib/profile/models";
import { DiscoverabilityToggle } from "./discoverability-toggle";

export const metadata = {
  title: "My profile",
};

export default function ProfilePage() {
  return (
    <main className="app-page">
      <h1>My profile</h1>
      <Suspense fallback={<p>Loading your profile…</p>}>
        <ProfileContent />
      </Suspense>
    </main>
  );
}

async function ProfileContent() {
  const load = await loadProtectedJson(["v1", "profile"]);
  if (load.kind === "unavailable") {
    return <p role="alert">Your profile is temporarily unavailable. Please try again.</p>;
  }
  if (load.kind === "signed-out") {
    return <p>Please sign in to view your profile.</p>;
  }
  if (load.kind === "not-eligible") {
    return <p>Your profile is available once your account is verified and set up.</p>;
  }

  const profile = parseOwnProfile(load.body);
  if (!profile) {
    return <p role="alert">Your profile is temporarily unavailable. Please try again.</p>;
  }

  return (
    <section className="profile-card" aria-labelledby="profile-name">
      <h2 id="profile-name">{profile.displayName}</h2>
      {profile.bio ? <p>{profile.bio}</p> : null}
      {profile.profilePrompt ? <p className="profile-prompt">{profile.profilePrompt}</p> : null}
      {profile.goals.length > 0 ? <p>Goals: {profile.goals.join(", ")}</p> : null}
      {profile.interests.length > 0 ? <p>Interests: {profile.interests.join(", ")}</p> : null}
      <DiscoverabilityToggle initialDiscoverable={profile.discoverable} />
    </section>
  );
}
