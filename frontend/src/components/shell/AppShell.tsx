import { Sidebar } from "./Sidebar";
import { BottomNav } from "./BottomNav";

export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-dvh">
      <Sidebar />
      <main
        className="mx-auto max-w-7xl px-4 pb-28 pt-6 sm:px-8 sm:pb-6 sm:pl-24"
        style={{ paddingBottom: "calc(env(safe-area-inset-bottom) + 112px)" }}
      >
        <div className="relative">
          <div className="pointer-events-none absolute inset-x-6 top-0 h-px bg-gradient-to-r from-transparent via-white/14 to-transparent" />
          {children}
        </div>
      </main>
      <BottomNav />
    </div>
  );
}
