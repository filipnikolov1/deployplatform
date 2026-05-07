import { Suspense } from "react";
import { AppShell } from "@/components/shell/AppShell";
import { AppGrid } from "@/components/dashboard/AppGrid";
import { ErrorBoundary } from "@/components/ErrorBoundary";

export default function Home() {
  return (
    <AppShell>
      <ErrorBoundary>
        <Suspense fallback={null}>
          <AppGrid />
        </Suspense>
      </ErrorBoundary>
    </AppShell>
  );
}
