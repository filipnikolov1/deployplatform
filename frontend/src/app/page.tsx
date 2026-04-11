import { AppShell } from "@/components/shell/AppShell";
import { ToastProvider } from "@/hooks/useToast";

export default function Home() {
  return (
    <ToastProvider>
      <AppShell>
        <h1 className="text-2xl font-semibold mb-4">Apps</h1>
        <p className="text-text-secondary">Shell loaded. Dashboard content coming in plan 2.</p>
      </AppShell>
    </ToastProvider>
  );
}
