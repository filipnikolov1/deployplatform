"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { LayoutGrid, Rocket, ScrollText, Settings } from "lucide-react";

const items = [
  { href: "/", label: "Apps", icon: LayoutGrid },
  { href: "/activity", label: "Activity", icon: ScrollText },
  { href: "/setup", label: "Setup", icon: Rocket },
  { href: "/settings", label: "Settings", icon: Settings },
] as const;

export function Sidebar() {
  const pathname = usePathname();

  return (
    <aside
      className="hidden md:flex fixed left-0 top-0 bottom-0 z-10 flex-col"
      style={{
        width: "var(--sidebar-width)",
        borderRight: "1px solid var(--c-border-1)",
        background: "rgba(8,8,14,0.72)",
        backdropFilter: "blur(20px)",
        WebkitBackdropFilter: "blur(20px)",
      }}
    >
      {/* Logo / wordmark */}
      <div
        className="flex items-center gap-2.5 px-5 py-[22px]"
        style={{ borderBottom: "1px solid var(--c-border-1)" }}
      >
        <div
          className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg"
          style={{ background: "linear-gradient(135deg, var(--c-accent-primary), var(--c-accent-light))" }}
        >
          <Rocket className="h-4 w-4 text-white" aria-hidden />
        </div>
        <span className="text-[15px] font-semibold tracking-[-0.01em]" style={{ color: "var(--c-fg-0)" }}>
          Vector
        </span>
      </div>

      {/* Nav items */}
      <nav className="flex flex-col gap-0.5 p-2.5" aria-label="Primary">
        {items.map((item) => {
          const Icon = item.icon;
          const isActive = pathname === item.href;
          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={isActive ? "page" : undefined}
              className="flex items-center gap-2.5 rounded-md px-3 py-2 text-sm font-medium transition-all duration-fast outline-none focus-visible:ring-2 focus-visible:ring-accent-light"
              style={{
                background: isActive ? "var(--c-accent-soft)" : "transparent",
                color: isActive ? "#DDD6FE" : "var(--c-fg-2)",
              }}
              onMouseEnter={(e) => {
                if (!isActive) {
                  e.currentTarget.style.background = "var(--c-surface-2)";
                  e.currentTarget.style.color = "var(--c-fg-1)";
                }
              }}
              onMouseLeave={(e) => {
                if (!isActive) {
                  e.currentTarget.style.background = "transparent";
                  e.currentTarget.style.color = "var(--c-fg-2)";
                }
              }}
            >
              <Icon className="h-4 w-4 shrink-0" />
              <span>{item.label}</span>
            </Link>
          );
        })}
      </nav>

      {/* Footer user strip */}
      <div
        className="mt-auto p-3"
        style={{ borderTop: "1px solid var(--c-border-1)" }}
      >
        <div
          className="flex items-center gap-2.5 rounded-md px-2.5 py-2"
          style={{
            background: "var(--c-surface-1)",
            border: "1px solid var(--c-border-1)",
          }}
        >
          <div
            className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-[11px] font-semibold text-white"
            style={{ background: "linear-gradient(135deg, #7C3AED, #3B82F6)" }}
          >
            OP
          </div>
          <div className="min-w-0">
            <div className="truncate text-[13px] font-medium" style={{ color: "var(--c-fg-1)" }}>
              Ops
            </div>
            <div className="truncate text-[11px]" style={{ color: "var(--c-fg-3)" }}>
              filipnikolov.dev
            </div>
          </div>
        </div>
      </div>
    </aside>
  );
}
