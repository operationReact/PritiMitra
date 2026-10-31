import type { Metadata } from "next";
import { Suspense } from "react";
import "./globals.css";
import { DesignExperienceProvider } from "./design-experience-provider";
import { ProductAppShell } from "./product-app-shell";
import {
  DEFAULT_DESIGN_EXPERIENCE,
  DESIGN_EXPERIENCE_STORAGE_KEY,
  DESIGN_EXPERIENCES,
} from "@/lib/design/experience";

export const metadata: Metadata = {
  title: "PritiMitra",
  description: "PritiMitra responsive web app",
};

// Runs before first paint so a stored Spark/Essence choice is applied without flicker.
const applyStoredExperience = `(function(){try{var v=localStorage.getItem(${JSON.stringify(DESIGN_EXPERIENCE_STORAGE_KEY)});if(${JSON.stringify(DESIGN_EXPERIENCES)}.indexOf(v)!==-1){document.documentElement.dataset.experience=v;}}catch(e){}})();`;

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" data-experience={DEFAULT_DESIGN_EXPERIENCE} suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: applyStoredExperience }} />
      </head>
      <body>
        <DesignExperienceProvider>
          <Suspense fallback={children}><ProductAppShell>{children}</ProductAppShell></Suspense>
        </DesignExperienceProvider>
      </body>
    </html>
  );
}
