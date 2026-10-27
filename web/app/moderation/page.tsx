import { ModerationQueue } from "./moderation-queue";

export const metadata = {
  title: "Trust & Safety | PritiMitra",
  robots: { index: false, follow: false },
};

export default function ModerationPage() {
  return <ModerationQueue />;
}
