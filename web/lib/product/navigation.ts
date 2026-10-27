export type ProductIcon = "home" | "discover" | "community" | "blind-date" | "messages" | "profile";

export type ProductNavItem = {
  href: string;
  label: string;
  mobileLabel: string;
  icon: ProductIcon;
};

export const PRODUCT_NAV_ITEMS: readonly ProductNavItem[] = [
  { href: "/home", label: "Home", mobileLabel: "Home", icon: "home" },
  { href: "/discover", label: "Discover", mobileLabel: "Discover", icon: "discover" },
  { href: "/community", label: "Community", mobileLabel: "Circle", icon: "community" },
  { href: "/blind-date", label: "Blind Date", mobileLabel: "Date", icon: "blind-date" },
  { href: "/messages", label: "Messages", mobileLabel: "Inbox", icon: "messages" },
  { href: "/profile", label: "My profile", mobileLabel: "Profile", icon: "profile" },
];

const PRODUCT_ROOTS = [
  "/home",
  "/discover",
  "/community",
  "/blind-date",
  "/messages",
  "/profile",
  "/account",
  "/moderation",
] as const;

/** Only authenticated product surfaces use the app shell; the public landing page stays independent. */
export function isProductRoute(pathname: string): boolean {
  return PRODUCT_ROOTS.some((root) => pathname === root || pathname.startsWith(root + "/"));
}

/** Match nested conversation routes and treat account settings as part of the profile destination. */
export function isProductNavActive(pathname: string, href: string): boolean {
  if (href === "/profile" && pathname === "/account") return true;
  return pathname === href || pathname.startsWith(href + "/");
}

export function productPageTitle(pathname: string): string {
  if (pathname.startsWith("/blind-date")) return "Blind Date";
  if (pathname.startsWith("/community")) return "Community";
  if (pathname.startsWith("/discover")) return "Discover";
  if (pathname.startsWith("/messages")) return pathname === "/messages" ? "Messages" : "Your conversation";
  if (pathname.startsWith("/profile")) return "My profile";
  if (pathname.startsWith("/moderation")) return "Trust & Safety";
  if (pathname.startsWith("/account")) return "Account";
  return "Your space";
}
