import type { Metadata } from "next";
import { Suspense } from "react";
import "./globals.css";
import { ProductAppShell } from "./product-app-shell";

export const metadata: Metadata = {
  title: "PritiMitra",
  description: "PritiMitra responsive web app",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en">
      <body><Suspense fallback={children}><ProductAppShell>{children}</ProductAppShell></Suspense></body>
    </html>
  );
}
