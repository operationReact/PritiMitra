"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { isProductNavActive, isProductRoute, PRODUCT_NAV_ITEMS, productPageTitle, type ProductIcon, type ProductNavItem } from "@/lib/product/navigation";

function NavigationIcon({ name }: { name: ProductIcon }) {
  const common = {
    width: 21,
    height: 21,
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 1.8,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
    "aria-hidden": true as const,
  };
  switch (name) {
    case "home":
      return <svg {...common}><path d="m3.5 10 8.5-7 8.5 7" /><path d="M5.5 9v11h13V9M9.5 20v-6h5v6" /></svg>;
    case "discover":
      return <svg {...common}><circle cx="12" cy="12" r="9" /><path d="m15.7 8.3-2.2 5.2-5.2 2.2 2.2-5.2 5.2-2.2Z" /></svg>;
    case "community":
      return <svg {...common}><circle cx="9" cy="8" r="3.2" /><path d="M3 20v-1.5a5.8 5.8 0 0 1 11.6 0V20" /><path d="M16 5.2a3.2 3.2 0 0 1 0 6.1M17.4 14.5a5.1 5.1 0 0 1 3.6 4.8V20" /></svg>;
    case "blind-date":
      return <svg {...common}><rect x="3.5" y="5" width="17" height="16" rx="3" /><path d="M7.5 3v4M16.5 3v4M3.5 10h17" /><path d="M12 17s-3-1.7-3-3.4a1.8 1.8 0 0 1 3-1.2 1.8 1.8 0 0 1 3 1.2C15 15.3 12 17 12 17Z" /></svg>;
    case "messages":
      return <svg {...common}><path d="M20.5 11.5a7.5 7.5 0 0 1-7.8 7.5 8.8 8.8 0 0 1-3.2-.6L4 20l1.4-4A7 7 0 0 1 4 11.5 7.5 7.5 0 0 1 11.5 4h1.2a7.5 7.5 0 0 1 7.8 7.5Z" /><path d="M8 11.5h.1M12 11.5h.1M16 11.5h.1" /></svg>;
    case "profile":
      return <svg {...common}><circle cx="12" cy="8" r="3.5" /><path d="M4.5 20a7.5 7.5 0 0 1 15 0" /></svg>;
  }
}

function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <Link className={compact ? "product-brand product-brand-compact" : "product-brand"} href="/home" aria-label="PritiMitra home">
      <Image src="/brand/image_logo.png" width={38} height={34} alt="" priority />
      {!compact ? <Image className="product-wordmark" src="/brand/wordmark.png" width={110} height={37} alt="PritiMitra" priority /> : null}
    </Link>
  );
}

function NavLink({ item, pathname, mobile = false }: { item: ProductNavItem; pathname: string; mobile?: boolean }) {
  const active = isProductNavActive(pathname, item.href);
  return (
    <Link
      href={item.href}
      className={`product-nav-link${active ? " is-active" : ""}${mobile ? " product-nav-link-mobile" : ""}`}
      aria-current={active ? "page" : undefined}
      title={item.label}
    >
      <span className="product-nav-icon"><NavigationIcon name={item.icon} /></span>
      <span className="product-nav-label">{mobile ? item.mobileLabel : item.label}</span>
      {item.href === "/messages" ? <span className="product-nav-status" aria-label="Inbox" /> : null}
    </Link>
  );
}

export function ProductAppShell({ children }: { children: ReactNode }) {
  const currentPath = usePathname() || "/";
  if (!isProductRoute(currentPath)) return <>{children}</>;

  return (
    <div className="product-app-shell">
      <aside className="product-sidebar" aria-label="PritiMitra workspace">
        <Brand />
        <div className="product-sidebar-divider" />
        <p className="product-nav-caption">YOUR SPACE</p>
        <nav className="product-main-nav" aria-label="Primary navigation">
          {PRODUCT_NAV_ITEMS.map((item) => <NavLink key={item.href} item={item} pathname={currentPath} />)}
        </nav>
        <div className="product-sidebar-spacer" />
        <div className="product-safety-card">
          <span className="product-safety-icon" aria-hidden="true">♡</span>
          <p>Connection first.<br />Consent, always.</p>
          <span>Made for your pace</span>
        </div>
        <Link className="product-sidebar-profile" href="/profile">
          <span className="product-profile-avatar" aria-hidden="true">♡</span>
          <span><strong>Your profile</strong><small>Make it feel like you</small></span>
          <span className="product-profile-arrow" aria-hidden="true">↗</span>
        </Link>
      </aside>

      <div className="product-workspace">
        <header className="product-topbar">
          <div className="product-topbar-heading">
            <Brand compact />
            <div className="product-topbar-title">
              <span>PRITIMITRA / YOUR SPACE</span>
              <strong>{productPageTitle(currentPath)}</strong>
            </div>
          </div>
          <div className="product-topbar-actions">
            <span className="product-private-pill"><span aria-hidden="true">✦</span> Your pace, your rules</span>
            <Link className="product-account-link" href="/profile">
              <span className="product-profile-avatar" aria-hidden="true"><NavigationIcon name="profile" /></span>
              <span>My profile</span>
            </Link>
          </div>
        </header>
        <div className="product-content">{children}</div>
      </div>

      <nav className="product-bottom-nav" aria-label="Mobile navigation">
        {PRODUCT_NAV_ITEMS.filter((item) => item.href !== "/profile").map((item) => (
          <NavLink key={item.href} item={item} pathname={currentPath} mobile />
        ))}
        <NavLink item={PRODUCT_NAV_ITEMS[5]} pathname={currentPath} mobile />
      </nav>
    </div>
  );
}
