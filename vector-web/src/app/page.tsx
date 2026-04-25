import { Suspense } from "react";
import { AppShell } from "@/components/shell/AppShell";
import { ToastProvider } from "@/hooks/useToast";
import { AppGrid } from "@/components/dashboard/AppGrid";

export default function Home() {
  return (
    <ToastProvider>
      <AppShell>
        <Suspense fallback={null}>
          <AppGrid />
        </Suspense>
      </AppShell>
    </ToastProvider>
  );
}
