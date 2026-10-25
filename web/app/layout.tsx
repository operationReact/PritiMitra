import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "PritiMitra",
  description: "PritiMitra responsive web app",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
