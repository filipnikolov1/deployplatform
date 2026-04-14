import { Rocket, type LucideIcon } from "lucide-react";
import type { ReactNode } from "react";

interface EmptyStateProps {
  children: ReactNode;
}

function EmptyStateRoot({ children }: EmptyStateProps) {
  return (
    <div
      role="status"
      aria-live="polite"
      className="rounded-xl border-2 border-dashed border-white/[0.12] bg-white/[0.02] backdrop-blur-xl p-12 text-center max-w-lg mx-auto"
    >
      {children}
    </div>
  );
}

function Media({ icon: Icon = Rocket }: { icon?: LucideIcon }) {
  return (
    <div className="inline-flex items-center justify-center mb-6 p-3 rounded-lg bg-accent-ghost/20">
      <Icon className="h-12 w-12 text-accent-ghostLight" />
    </div>
  );
}

function Title({ children }: { children: ReactNode }) {
  return (
    <h2 className="text-lg font-semibold text-white mb-2 text-balance">
      {children}
    </h2>
  );
}

function Description({ children }: { children: ReactNode }) {
  return (
    <p className="text-sm text-slate-400 mb-6 max-w-md mx-auto">{children}</p>
  );
}

function Actions({ children }: { children: ReactNode }) {
  return (
    <div className="flex items-center justify-center gap-3 flex-wrap">
      {children}
    </div>
  );
}

export const EmptyState = Object.assign(EmptyStateRoot, {
  Media,
  Title,
  Description,
  Actions,
});
