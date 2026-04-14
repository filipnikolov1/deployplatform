"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { LayoutGrid, Rocket, ScrollText, Settings } from "lucide-react";
import { GlassCard } from "@/components/primitives/GlassCard";

export function Sidebar() {
  const pathname = usePathname();
  const items = [
    { href: "/", label: "Apps", icon: LayoutGrid },
    { href: "/activity", label: "Activity", icon: ScrollText },
    { href: "/setup", label: "Setup", icon: Rocket },
    { href: "/settings", label: "Settings", icon: Settings },
  ];

  return (
    <aside className="hidden sm:flex fixed left-4 top-4 bottom-4 w-16 z-10">
      <GlassCard variant="panel" className="flex flex-col items-center gap-4 w-full py-4">
        <nav aria-label="Primary">
          <div className="flex flex-col gap-2">
            {items.map((item) => {
              const Icon = item.icon;
              const isActive = pathname === item.href;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  aria-current={isActive ? "page" : undefined}
                  aria-label={item.label}
                  className={`group relative flex items-center justify-center h-12 w-12 rounded-lg transition-colors ${
                    isActive
                      ? "bg-accent-ghost/30 text-white before:content-[''] before:absolute before:left-0 before:w-[3px] before:h-full before:bg-accent-ghostLight before:rounded-r"
                      : "bg-accent-ghost/10 text-slate-400 hover:bg-accent-ghost/20 hover:text-slate-200"
                  }`}
                >
                  <Icon className="h-5 w-5" />
                  <span className="sr-only">{item.label}</span>
                  <span className="absolute left-full ml-2 px-2 py-1 rounded bg-black/80 border border-white/10 text-xs text-white whitespace-nowrap opacity-0 group-hover:opacity-100 pointer-events-none">
                    {item.label}
                  </span>
                </Link>
              );
            })}
          </div>
        </nav>
      </GlassCard>
    </aside>
  );
}
