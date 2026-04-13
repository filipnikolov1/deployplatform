"use client";

import { AppShell } from "@/components/shell/AppShell";
import { ToastProvider } from "@/hooks/useToast";
import { PreferencesForm } from "@/components/settings/PreferencesForm";

export default function SettingsPage() {
  return (
    <ToastProvider>
      <AppShell>
        <div className="mx-auto max-w-3xl px-4 py-12">
          <header className="mb-8">
            <h1 className="text-3xl font-semibold tracking-tight text-slate-100 mb-2">
              Settings
            </h1>
            <p className="text-slate-400">
              Notifications, appearance, and account preferences.
            </p>
          </header>
          <PreferencesForm />
        </div>
      </AppShell>
    </ToastProvider>
  );
}
