"use client";

import { AppShell } from "@/components/shell/AppShell";
import { ToastProvider } from "@/hooks/useToast";
import { SetupGuide } from "@/components/setup/SetupGuide";

export default function SetupPage() {
  return (
    <ToastProvider>
      <AppShell>
        <div className="flex flex-col max-w-[820px]">
          {/* Page header */}
          <div
            className="flex items-end justify-between gap-4 pb-5 mb-6 flex-wrap"
            style={{ borderBottom: "1px solid var(--c-border-1)" }}
          >
            <div>
              <h1
                className="text-2xl font-semibold tracking-[-0.01em] m-0"
                style={{ color: "var(--c-fg-0)", lineHeight: 1.2 }}
              >
                Setup guide
              </h1>
              <p className="mt-1.5 text-[13px]" style={{ color: "var(--c-fg-2)" }}>
                Configure repository, workflow, and Docker image publishing.
              </p>
            </div>
          </div>

          <SetupGuide />
        </div>
      </AppShell>
    </ToastProvider>
  );
}
