import { Sidebar } from "./Sidebar";
import { BottomNav } from "./BottomNav";

export function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-dvh">
      <Sidebar />
      <main
        className="sm:pl-24 pb-28 sm:pb-6 px-4 sm:px-8 pt-6 max-w-7xl mx-auto"
        style={{ paddingBottom: "calc(env(safe-area-inset-bottom) + 112px)" }}
      >
        {children}
      </main>
      <BottomNav />
    </div>
  );
}
