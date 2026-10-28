import type { Metadata } from "next";
import { Suspense } from "react";
import "./globals.css";
import { DesignExperienceProvider } from "./design-experience-provider";
import { ProductAppShell } from "./product-app-shell";

export const metadata: Metadata = {
  title: "PritiMitra",
  description: "PritiMitra responsive web app",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" data-experience="spark">
      <body>
        <DesignExperienceProvider>
          <Suspense fallback={children}><ProductAppShell>{children}</ProductAppShell></Suspense>
        </DesignExperienceProvider>
      </body>
    </html>
  );
}
