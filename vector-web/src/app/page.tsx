import { Suspense } from "react";
import { AppShell } from "@/components/shell/AppShell";
import { ToastProvider } from "@/hooks/useToast";
import { AppGrid } from "@/components/dashboard/AppGrid";
import { ErrorBoundary } from "@/components/ErrorBoundary";

export default function Home() {
  return (
    <ToastProvider>
      <AppShell>
        <ErrorBoundary>
          <Suspense fallback={null}>
            <AppGrid />
          </Suspense>
        </ErrorBoundary>
      </AppShell>
    </ToastProvider>
  );
}
