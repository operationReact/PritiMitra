"use client";

import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";

type Group = { publicId: string; slug: string; name: string; description: string; joined: boolean; memberCount: number };
type Post = {
  postPublicId: string;
  groupPublicId: string;
  displayName: string;
  body: string;
  likeCount: number;
  heartCount: number;
  supportCount: number;
  myReaction: string | null;
  createdAt: string;
  mine: boolean;
};
type PostResponse = { items: Post[]; nextCursor: string | null };

async function api<T>(path: string, method = "GET", payload?: unknown): Promise<T> {
  const response = await fetch(path, {
    method,
    cache: "no-store",
    headers: payload === undefined ? undefined : { "Content-Type": "application/json" },
    body: payload === undefined ? undefined : JSON.stringify(payload),
  });
  if (!response.ok) {
    throw new Error(response.status === 401 ? "Your session has expired. Please sign in again." : "The request could not be completed. Please try again.");
  }
  if (response.status === 204) return undefined as T;
  return await response.json() as T;
}
function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
function parseGroups(value: unknown): Group[] {
  if (!isRecord(value) || !Array.isArray(value.items)) throw new Error("Community list was invalid.");
  return value.items.filter((item): item is Group => isRecord(item) &&
    typeof item.publicId === "string" && typeof item.slug === "string" &&
    typeof item.name === "string" && typeof item.description === "string" &&
    typeof item.joined === "boolean" && typeof item.memberCount === "number");
}
function parsePosts(value: unknown): PostResponse {
  if (!isRecord(value) || !Array.isArray(value.items) || !(value.nextCursor === null || typeof value.nextCursor === "string")) {
    throw new Error("Community feed was invalid.");
  }
  const items = (value.items as unknown[]).filter((item): item is Post => isRecord(item) &&
    typeof item.postPublicId === "string" && typeof item.groupPublicId === "string" &&
    typeof item.body === "string" && typeof item.displayName === "string" &&
    typeof item.createdAt === "string" && typeof item.mine === "boolean" &&
    typeof item.likeCount === "number" && typeof item.heartCount === "number" &&
    typeof item.supportCount === "number" &&
    (item.myReaction === null || typeof item.myReaction === "string"));
  return { items, nextCursor: value.nextCursor };
}
function dateLabel(value: string): string {
  const time = new Date(value);
  return Number.isNaN(time.getTime()) ? "Date unavailable" :
    new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(time);
}

export function CommunityBrowser() {
  const [groups, setGroups] = useState<Group[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [posts, setPosts] = useState<Post[]>([]);
  const [cursor, setCursor] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const [loadingGroups, setLoadingGroups] = useState(true);
  const [loadingPosts, setLoadingPosts] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const selected = useMemo(() => groups.find((group) => group.publicId === selectedId) ?? null, [groups, selectedId]);

  const refreshGroups = useCallback(async () => {
    try {
      const payload = await api<unknown>("/api/backend/v1/community/groups");
      const nextGroups = parseGroups(payload);
      setGroups(nextGroups);
      setSelectedId((current) => current && nextGroups.some((group) => group.publicId === current) ? current : null);
      setError(null);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Communities are unavailable.");
    } finally {
      setLoadingGroups(false);
    }
  }, []);
  useEffect(() => { void Promise.resolve().then(refreshGroups); }, [refreshGroups]);

  const refreshPosts = useCallback(async (groupId: string, nextCursor?: string | null) => {
    setLoadingPosts(true);
    try {
      const query = new URLSearchParams({ size: "20" });
      if (nextCursor) query.set("cursor", nextCursor);
      const page = parsePosts(await api<unknown>("/api/backend/v1/community/groups/" + groupId + "/posts?" + query.toString()));
      setPosts((current) => nextCursor ? [...current, ...page.items] : page.items);
      setCursor(page.nextCursor);
      setError(null);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "The community feed is unavailable.");
    } finally {
      setLoadingPosts(false);
    }
  }, []);

  async function selectGroup(group: Group) {
    setSelectedId(group.publicId);
    setPosts([]);
    setCursor(null);
    setError(null);
    setNotice(null);
    if (group.joined) await refreshPosts(group.publicId);
    else setLoadingPosts(false);
  }

  async function toggleMembership(group: Group) {
    setSaving(true); setError(null); setNotice(null);
    try {
      if (group.joined) {
        await api("/api/backend/v1/community/groups/" + group.publicId + "/membership", "DELETE");
        setGroups((all) => all.map((item) => item.publicId === group.publicId ?
          { ...item, joined: false, memberCount: Math.max(0, item.memberCount - 1) } : item));
        setPosts([]); setCursor(null);
        setNotice("You left " + group.name + ".");
      } else {
        await api("/api/backend/v1/community/groups/" + group.publicId + "/membership", "PUT");
        setGroups((all) => all.map((item) => item.publicId === group.publicId ?
          { ...item, joined: true, memberCount: item.memberCount + 1 } : item));
        setSelectedId(group.publicId);
        setNotice("You joined " + group.name + ".");
        await refreshPosts(group.publicId);
      }
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Membership could not be updated.");
    } finally {
      setSaving(false);
    }
  }
  async function createPost(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected?.joined || draft.trim().length === 0) return;
    setSaving(true); setError(null); setNotice(null);
    try {
      await api("/api/backend/v1/community/groups/" + selected.publicId + "/posts", "POST", { body: draft });
      setDraft("");
      await refreshPosts(selected.publicId);
      setNotice("Your post was published.");
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Your post could not be published.");
    } finally {
      setSaving(false);
    }
  }
  async function react(post: Post, reaction: "LIKE" | "HEART" | "SUPPORT") {
    setSaving(true); setError(null);
    try {
      const path = "/api/backend/v1/community/posts/" + post.postPublicId + "/reaction";
      if (post.myReaction === reaction) await api(path, "DELETE");
      else await api(path, "PUT", { reaction });
      if (selected) await refreshPosts(selected.publicId);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Reaction could not be saved.");
    } finally {
      setSaving(false);
    }
  }
  async function removePost(post: Post) {
    if (!selected) return;
    setSaving(true); setError(null); setNotice(null);
    try {
      await api("/api/backend/v1/community/posts/" + post.postPublicId, "DELETE");
      await refreshPosts(selected.publicId);
      setNotice("Your post was removed.");
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Post could not be removed.");
    } finally {
      setSaving(false);
    }
  }
  async function reportPost(post: Post) {
    setSaving(true); setError(null); setNotice(null);
    try {
      await api("/api/backend/v1/reports", "POST", {
        targetType: "COMMUNITY_POST", targetId: post.postPublicId, category: "UNWANTED_CONTENT",
      });
      setNotice("The post was reported privately for review.");
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "The post could not be reported.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="phase6-layout">
      <section className="phase6-panel" aria-labelledby="communities-title">
        <div className="phase6-section-heading">
          <div><p className="phase6-eyebrow">Find your people</p><h2 id="communities-title">Communities</h2></div>
          <span className="phase6-count">{groups.length}</span>
        </div>
        <p className="phase6-muted">Joining is optional. Only members can read and post in a community.</p>
        {loadingGroups ? <p role="status">Loading communities…</p> :
          groups.length === 0 ? <p>No communities are available yet.</p> :
          <ul className="phase6-group-list">{groups.map((group) => (
            <li key={group.publicId} className={selectedId === group.publicId ? "phase6-group active" : "phase6-group"}>
              <button className="phase6-group-select" onClick={() => void selectGroup(group)} aria-pressed={selectedId === group.publicId}>
                <span className="phase6-group-title">{group.name}</span>
                <span className="phase6-muted">{group.memberCount} members</span>
                <span>{group.description}</span>
              </button>
              <button className="phase6-secondary" onClick={() => void toggleMembership(group)} disabled={saving}>
                {group.joined ? "Leave" : "Join"}
              </button>
            </li>
          ))}</ul>}
      </section>
      <section className="phase6-panel" aria-labelledby="community-feed-title">
        <div className="phase6-section-heading">
          <div><p className="phase6-eyebrow">A respectful shared space</p><h2 id="community-feed-title">{selected?.name ?? "Community feed"}</h2></div>
        </div>
        {!selected ? <p className="phase6-muted">Choose a community to see its feed.</p> :
          !selected.joined ? <p className="phase6-muted">Join this community to read posts and participate.</p> : <>
            <form className="phase6-post-form" onSubmit={createPost}>
              <label htmlFor="community-post">Share something with the group</label>
              <textarea id="community-post" value={draft} maxLength={2000} onChange={(event) => setDraft(event.target.value)}
                rows={3} placeholder="Keep it kind, respectful, and safe." />
              <div className="phase6-form-footer"><span className="phase6-muted">{draft.length}/2000</span>
                <button className="phase6-primary" disabled={saving || draft.trim().length === 0}>Publish post</button>
              </div>
            </form>
            {loadingPosts && posts.length === 0 ? <p role="status">Loading posts…</p> :
              posts.length === 0 ? <p className="phase6-muted">No posts yet. Start the conversation.</p> :
              <div className="phase6-post-list">{posts.map((post) => (
                <article key={post.postPublicId} className="phase6-post">
                  <div className="phase6-post-meta"><strong>{post.displayName}</strong><time dateTime={post.createdAt}>{dateLabel(post.createdAt)}</time></div>
                  <p className="phase6-post-body">{post.body}</p>
                  <div className="phase6-reactions">
                    <button aria-pressed={post.myReaction === "LIKE"} onClick={() => void react(post, "LIKE")} disabled={saving}>Like · {post.likeCount}</button>
                    <button aria-pressed={post.myReaction === "HEART"} onClick={() => void react(post, "HEART")} disabled={saving}>Heart · {post.heartCount}</button>
                    <button aria-pressed={post.myReaction === "SUPPORT"} onClick={() => void react(post, "SUPPORT")} disabled={saving}>Support · {post.supportCount}</button>
                    {post.mine ? <button onClick={() => void removePost(post)} disabled={saving}>Remove</button> :
                      <button onClick={() => void reportPost(post)} disabled={saving}>Report</button>}
                  </div>
                </article>
              ))}</div>}
            {cursor ? <button className="phase6-secondary phase6-load-more" disabled={loadingPosts}
              onClick={() => void refreshPosts(selected.publicId, cursor)}>Load more posts</button> : null}
          </>}
      </section>
      {error ? <p className="phase6-error" role="alert">{error}</p> : null}
      {notice ? <p className="phase6-notice" role="status">{notice}</p> : null}
    </div>
  );
}
