"use client";

/**
 * FilterPills — three pill filters for the Activity page.
 * Active pill uses motion.span layoutId for smooth morphing animation.
 * State persisted in URL search param (?filter=failures) via useSearchParams + useRouter.
 */

import { useRouter, useSearchParams } from "next/navigation";
import { motion } from "framer-motion";
import { M } from "@/design/tokens";

export type ActivityFilter = "all" | "deploys" | "failures";

const PILLS: { id: ActivityFilter; label: string }[] = [
  { id: "all", label: "All" },
  { id: "deploys", label: "Deploys" },
  { id: "failures", label: "Failures" },
];

export function FilterPills() {
  const searchParams = useSearchParams();
  const router = useRouter();

  const rawFilter = searchParams.get("filter") ?? "all";
  const filter: ActivityFilter = (["all", "deploys", "failures"].includes(rawFilter)
    ? rawFilter
    : "all") as ActivityFilter;

  function setFilter(id: ActivityFilter) {
    const params = new URLSearchParams(searchParams.toString());
    if (id === "all") {
      params.delete("filter");
    } else {
      params.set("filter", id);
    }
    router.push(`?${params.toString()}`, { scroll: false });
  }

  return (
    <div
      style={{
        display: "inline-flex",
        gap: 4,
        padding: 4,
        background: M.surface,
        border: `1px solid ${M.line}`,
        borderRadius: M.rPill,
      }}
    >
      {PILLS.map((pill) => {
        const active = pill.id === filter;
        return (
          <button
            key={pill.id}
            type="button"
            onClick={() => setFilter(pill.id)}
            style={{
              position: "relative",
              padding: "6px 14px",
              borderRadius: M.rPill,
              background: "transparent",
              border: "none",
              cursor: "pointer",
              fontFamily: M.fontSans,
              fontSize: 12.5,
              fontWeight: 500,
              color: active ? M.fg : M.fg2,
              letterSpacing: "-0.005em",
              transition: "color 150ms",
            }}
          >
            {active && (
              <motion.span
                layoutId="activity-filter"
                style={{
                  position: "absolute",
                  inset: 0,
                  borderRadius: M.rPill,
                  background: M.surface2,
                  border: `1px solid ${M.line2}`,
                  zIndex: 0,
                }}
                transition={{ type: "spring", stiffness: 380, damping: 32 }}
              />
            )}
            <span style={{ position: "relative", zIndex: 1 }}>{pill.label}</span>
          </button>
        );
      })}
    </div>
  );
}
