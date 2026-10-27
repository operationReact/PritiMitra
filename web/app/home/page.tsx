import Link from "next/link";
import { Suspense } from "react";
import { loadProtectedJson } from "@/lib/profile/server";
import { parseOwnProfile } from "@/lib/profile/models";
import { parseConversationPage } from "@/lib/messaging/models";

export const metadata = {
  title: "Your space · PritiMitra",
  description: "Your private PritiMitra connection space.",
};

type JsonRecord = Record<string, unknown>;

function isRecord(value: unknown): value is JsonRecord {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function itemsFrom(value: unknown): JsonRecord[] | null {
  if (!isRecord(value) || !Array.isArray(value.items)) return null;
  return value.items.filter(isRecord);
}

function HomeSkeleton() {
  return (
    <main className="home-dashboard" aria-label="Loading your space">
      <div className="home-skeleton home-skeleton-hero" />
      <div className="home-skeleton-grid"><div className="home-skeleton" /><div className="home-skeleton" /><div className="home-skeleton" /><div className="home-skeleton" /></div>
      <div className="home-skeleton home-skeleton-feature" />
    </main>
  );
}

export default function HomePage() {
  return <Suspense fallback={<HomeSkeleton />}><HomeDashboard /></Suspense>;
}

async function HomeDashboard() {
  const [profileLoad, conversationLoad, groupLoad, blindDateLoad] = await Promise.all([
    loadProtectedJson(["v1", "profile"]),
    loadProtectedJson(["v1", "conversations"]),
    loadProtectedJson(["v1", "community", "groups"]),
    loadProtectedJson(["v1", "blind-dates", "my"]),
  ]);

  if (profileLoad.kind === "signed-out" || profileLoad.kind === "not-eligible") {
    return (
      <main className="home-dashboard home-gate-wrap">
        <section className="home-access-card">
          <span className="home-access-mark" aria-hidden="true">♡</span>
          <p className="home-eyebrow">WELCOME TO YOUR SPACE</p>
          <h1>Let’s get you settled in.</h1>
          <p>Your PritiMitra space is available when you’re signed in and your account setup is complete.</p>
          <Link className="home-action-primary" href="/account">Check account status <span aria-hidden="true">↗</span></Link>
        </section>
      </main>
    );
  }
  if (profileLoad.kind !== "ok") {
    return (
      <main className="home-dashboard home-gate-wrap">
        <section className="home-access-card">
          <span className="home-access-mark" aria-hidden="true">✦</span>
          <p className="home-eyebrow">JUST A MOMENT</p>
          <h1>Your space is taking a pause.</h1>
          <p>We couldn’t load your profile just now. Your information hasn’t been changed.</p>
          <Link className="home-action-secondary" href="/home">Try again <span aria-hidden="true">↻</span></Link>
        </section>
      </main>
    );
  }

  const profile = parseOwnProfile(profileLoad.body);
  if (!profile) {
    return <main className="home-dashboard"><p className="home-inline-error" role="alert">Your profile response could not be read. Please try again later.</p></main>;
  }

  const conversations = conversationLoad.kind === "ok" ? parseConversationPage(conversationLoad.body) : null;
  const groups = groupLoad.kind === "ok" ? itemsFrom(groupLoad.body) : null;
  const bookings = blindDateLoad.kind === "ok" ? itemsFrom(blindDateLoad.body) : null;
  const joinedGroups = groups?.filter((group) => group.joined === true).length ?? null;
  const unread = conversations?.items.reduce((sum, item) => sum + item.unreadCount, 0) ?? null;
  const activeBookings = bookings?.filter((item) => item.status === "WAITING" || item.status === "PAIRED").length ?? null;
  const displayName = profile.displayName.trim().split(/\s+/)[0] || "there";

  const stats = [
    { label: "Conversations", value: conversations ? String(conversations.items.length) : "—", detail: unread === null ? "Inbox status unavailable" : unread > 0 ? `${unread} waiting to be read` : "You’re all caught up", icon: "↗", tint: "lilac", href: "/messages" },
    { label: "Communities", value: joinedGroups === null ? "—" : String(joinedGroups), detail: joinedGroups === null ? "Membership status unavailable" : "Spaces you chose", icon: "✧", tint: "peach", href: "/community" },
    { label: "Blind Date", value: activeBookings === null ? "—" : String(activeBookings), detail: activeBookings === null ? "Schedule unavailable" : "Active opt-ins and pairings", icon: "♡", tint: "mint", href: "/blind-date" },
    { label: "Discovery", value: profile.discoverable ? "Visible" : "Private", detail: profile.discoverable ? "Your profile is discoverable" : "Only you can see your profile", icon: "✦", tint: "rose", href: "/profile" },
  ];

  return (
    <main className="home-dashboard">
      <section className="home-welcome" aria-labelledby="home-title">
        <div className="home-welcome-copy">
          <p className="home-eyebrow"><span className="home-eyebrow-dot" />A LITTLE SPACE TO BE YOU</p>
          <h1 id="home-title">Welcome back, {displayName}<span className="home-title-heart" aria-hidden="true">♡</span></h1>
          <p className="home-welcome-description">The best connections begin when you can show up as yourself. Find your people, at your own pace.</p>
          <div className="home-welcome-actions">
            <Link className="home-action-primary" href="/discover">Explore people <span aria-hidden="true">↗</span></Link>
            <Link className="home-action-ghost" href="/community">Find your community</Link>
          </div>
          <div className="home-welcome-footnote"><span aria-hidden="true">✦</span> Connection first. Consent, always.</div>
        </div>
        <div className="home-welcome-art" aria-hidden="true">
          <div className="home-art-orbit home-art-orbit-one" />
          <div className="home-art-orbit home-art-orbit-two" />
          <div className="home-art-sun">♡</div>
          <div className="home-art-chip home-art-chip-one"><span>✦</span><div><strong>Be yourself</strong><small>That’s the whole point</small></div></div>
          <div className="home-art-chip home-art-chip-two"><span>⌁</span><div><strong>Find your people</strong><small>One hello at a time</small></div></div>
          <div className="home-art-spark home-art-spark-one">✦</div>
          <div className="home-art-spark home-art-spark-two">✧</div>
        </div>
      </section>

      <section className="home-section" aria-labelledby="home-overview-title">
        <div className="home-section-heading">
          <div><p className="home-section-kicker">YOUR OVERVIEW</p><h2 id="home-overview-title">Your corner of PritiMitra</h2></div>
          <Link href="/profile" className="home-text-link">Manage your profile <span aria-hidden="true">↗</span></Link>
        </div>
        <div className="home-stats-grid">
          {stats.map((stat) => (
            <Link href={stat.href} key={stat.label} className="home-stat-card">
              <span className={`home-stat-icon home-stat-${stat.tint}`} aria-hidden="true">{stat.icon}</span>
              <span className="home-stat-label">{stat.label}</span>
              <strong className="home-stat-value">{stat.value}</strong>
              <span className="home-stat-detail">{stat.detail}</span>
              <span className="home-stat-arrow" aria-hidden="true">↗</span>
            </Link>
          ))}
        </div>
      </section>

      <section className="home-section" aria-labelledby="home-paths-title">
        <div className="home-section-heading">
          <div><p className="home-section-kicker">MAKE IT YOURS</p><h2 id="home-paths-title">Choose your next little adventure</h2></div>
          <p className="home-section-aside">No rush. No pressure.</p>
        </div>
        <div className="home-feature-grid">
          <Link href="/discover" className="home-feature-card home-feature-discover">
            <span className="home-feature-art" aria-hidden="true"><span>✳</span><i>♡</i></span>
            <span className="home-feature-tag">MEET PEOPLE</span><h3>Discover connections</h3>
            <p>Meet people who share your interests, energy, and hopes for connection.</p><span className="home-feature-link">Explore profiles <span aria-hidden="true">↗</span></span>
          </Link>
          <Link href="/community" className="home-feature-card home-feature-community">
            <span className="home-feature-art" aria-hidden="true"><span>✧</span><i>☼</i></span>
            <span className="home-feature-tag">FIND YOUR PEOPLE</span><h3>Find your community</h3>
            <p>Join interest-led spaces for friendship, identity, belonging, and events.</p><span className="home-feature-link">Explore communities <span aria-hidden="true">↗</span></span>
          </Link>
          <Link href="/blind-date" className="home-feature-card home-feature-blind-date">
            <span className="home-feature-art" aria-hidden="true"><span>♡</span><i>✦</i></span>
            <span className="home-feature-tag">A GENTLE HELLO</span><h3>Try a Blind Date</h3>
            <p>Choose a time slot. A pairing happens only when another person chooses it too.</p><span className="home-feature-link">Choose a time <span aria-hidden="true">↗</span></span>
          </Link>
          <Link href="/messages" className="home-feature-card home-feature-messages">
            <span className="home-feature-art" aria-hidden="true"><span>〰</span><i>♡</i></span>
            <span className="home-feature-tag">KEEP THE CONVERSATION GOING</span><h3>Your messages</h3>
            <p>Pick up a conversation or say hello when a mutual connection opens the door.</p><span className="home-feature-link">Open inbox <span aria-hidden="true">↗</span></span>
          </Link>
        </div>
      </section>

      <section className="home-privacy-panel">
        <div className="home-privacy-mark" aria-hidden="true">♡</div>
        <div><p className="home-section-kicker">YOUR SPACE, YOUR CHOICES</p><h2>You’re in control of how you show up.</h2><p>Your discovery visibility is currently <strong>{profile.discoverable ? "on" : "off"}</strong>. You can change it anytime from your profile.</p></div>
        <Link className="home-privacy-link" href="/profile">Review privacy <span aria-hidden="true">↗</span></Link>
      </section>
    </main>
  );
}
