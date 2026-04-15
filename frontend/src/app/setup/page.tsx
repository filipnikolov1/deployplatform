"use client";

import { AppShell } from "@/components/shell/AppShell";
import { ToastProvider } from "@/hooks/useToast";
import { SetupGuide } from "@/components/setup/SetupGuide";

export default function SetupPage() {
  return (
    <ToastProvider>
      <AppShell>
        <div className="mx-auto max-w-6xl py-6">
          <section className="px-1 sm:px-0">
            <SetupGuide />
          </section>
        </div>
      </AppShell>
    </ToastProvider>
  );
}
