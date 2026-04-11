import { AppShell } from "@/components/shell/AppShell";
import { ToastProvider } from "@/hooks/useToast";
import { AppGrid } from "@/components/dashboard/AppGrid";

export default function Home() {
  return (
    <ToastProvider>
      <AppShell>
        <AppGrid />
      </AppShell>
    </ToastProvider>
  );
}
