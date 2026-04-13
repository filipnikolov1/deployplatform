"use client";

import { AppShell } from "@/components/shell/AppShell";
import { ToastProvider } from "@/hooks/useToast";
import { SetupGuide } from "@/components/setup/SetupGuide";

export default function SetupPage() {
  return (
    <ToastProvider>
      <AppShell>
        <div className="mx-auto max-w-5xl px-4 py-12">
          <SetupGuide />
        </div>
      </AppShell>
    </ToastProvider>
  );
}
