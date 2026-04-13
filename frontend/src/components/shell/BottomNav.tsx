import Link from "next/link";
import { LayoutGrid, Wrench } from "lucide-react";
import { GlassCard } from "@/components/primitives/GlassCard";

export function BottomNav() {
  return (
    <div
      className="sm:hidden fixed bottom-0 left-0 right-0 z-10 p-3"
      style={{ paddingBottom: "calc(env(safe-area-inset-bottom) + 12px)" }}
    >
      <GlassCard variant="panel" className="flex justify-around items-center py-3">
        <Link
          href="/"
          aria-label="Apps"
          className="flex h-11 w-11 items-center justify-center rounded-full text-accent-ghostLight"
        >
          <LayoutGrid className="h-5 w-5" aria-hidden="true" />
        </Link>
        <Link
          href="/setup"
          aria-label="Setup"
          className="flex h-11 w-11 items-center justify-center rounded-full text-slate-300"
        >
          <Wrench className="h-5 w-5" aria-hidden="true" />
        </Link>
      </GlassCard>
    </div>
  );
}
