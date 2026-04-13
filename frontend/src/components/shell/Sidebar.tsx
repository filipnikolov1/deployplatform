import Link from "next/link";
import { LayoutGrid, Rocket, Wrench } from "lucide-react";
import { GlassCard } from "@/components/primitives/GlassCard";

export function Sidebar() {
  return (
    <aside className="hidden sm:flex fixed left-4 top-4 bottom-4 w-16 z-10">
      <GlassCard variant="panel" className="flex flex-col items-center gap-4 w-full py-4">
        <Rocket className="h-6 w-6 text-accent-ghostLight" aria-hidden="true" />
        <nav aria-label="Primary">
          <div className="flex flex-col gap-2">
            <Link
              href="/"
              aria-label="Apps"
              className="flex h-11 w-11 items-center justify-center rounded-full bg-accent-ghost/20 text-accent-ghostLight hover:bg-accent-ghost/30 transition-colors duration-ui"
            >
              <LayoutGrid className="h-5 w-5" aria-hidden="true" />
            </Link>
            <Link
              href="/setup"
              aria-label="Setup"
              className="flex h-11 w-11 items-center justify-center rounded-full text-slate-300 hover:bg-white/10 transition-colors duration-ui"
            >
              <Wrench className="h-5 w-5" aria-hidden="true" />
            </Link>
          </div>
        </nav>
      </GlassCard>
    </aside>
  );
}
