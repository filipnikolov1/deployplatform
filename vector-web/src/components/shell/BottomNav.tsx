"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { LayoutGrid, Rocket, ScrollText, Settings } from "lucide-react";
import { GlassCard } from "@/components/primitives/GlassCard";

export function BottomNav() {
  const pathname = usePathname();
  const items = [
    { href: "/", label: "Apps", icon: LayoutGrid },
    { href: "/activity", label: "Activity", icon: ScrollText },
    { href: "/setup", label: "Setup", icon: Rocket },
    { href: "/settings", label: "Settings", icon: Settings },
  ];

  return (
    <div
      className="sm:hidden fixed bottom-0 left-0 right-0 z-10 p-3"
      style={{ paddingBottom: "calc(env(safe-area-inset-bottom) + 12px)" }}
    >
      <GlassCard
        variant="panel"
        className="pb-[env(safe-area-inset-bottom)] bg-black/80 backdrop-blur-xl border-t border-white/[0.08] grid grid-cols-4"
      >
        {items.map((item) => {
          const Icon = item.icon;
          const isActive = pathname === item.href;
          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={isActive ? "page" : undefined}
              className={`flex flex-col items-center gap-1 min-h-[48px] px-3 py-2 rounded-lg transition-colors ${
                isActive ? "text-accent-light" : "text-slate-400"
              }`}
            >
              <Icon className="h-5 w-5" />
              <span className="text-[10px] font-medium">{item.label}</span>
            </Link>
          );
        })}
      </GlassCard>
    </div>
  );
}
