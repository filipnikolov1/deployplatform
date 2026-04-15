"use client";

import { AppShell } from "@/components/shell/AppShell";
import { ToastProvider } from "@/hooks/useToast";
import { PreferencesForm } from "@/components/settings/PreferencesForm";

export default function SettingsPage() {
  return (
    <ToastProvider>
      <AppShell>
        <div className="mx-auto max-w-4xl py-6">
          <section className="px-1 sm:px-0">
            <header className="glass-page-header">
              <div className="glass-kicker">Refinement</div>
              <h1 className="glass-title">Settings</h1>
              <p className="glass-subtitle">
                Notifications, appearance, and dashboard behavior tuned inside the same glass surface.
              </p>
            </header>
            <PreferencesForm />
          </section>
        </div>
      </AppShell>
    </ToastProvider>
  );
}
