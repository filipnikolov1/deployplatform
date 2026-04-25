"use client";

import { ReactNode, useState, KeyboardEvent } from "react";

export interface Tab {
  id: string;
  label: string;
  panel: ReactNode;
}

interface Props {
  tabs: Tab[];
  initialId?: string;
  onChange?: (id: string) => void;
}

export function Tabs({ tabs, initialId, onChange }: Props) {
  const [active, setActive] = useState(initialId ?? tabs[0]?.id);

  const select = (id: string) => {
    setActive(id);
    onChange?.(id);
  };

  const onKey = (e: KeyboardEvent<HTMLButtonElement>, idx: number) => {
    if (e.key === "ArrowRight") {
      e.preventDefault();
      select(tabs[(idx + 1) % tabs.length].id);
    } else if (e.key === "ArrowLeft") {
      e.preventDefault();
      select(tabs[(idx - 1 + tabs.length) % tabs.length].id);
    }
  };

  return (
    <div className="flex flex-col gap-4">
      <div
        role="tablist"
        className={`grid gap-1 bg-white/[0.04] border border-white/[0.08] rounded-lg p-1 ${
          tabs.length === 4 ? "grid-cols-4" : "grid-cols-2"
        }`}
      >
        {tabs.map((tab, idx) => {
          const selected = tab.id === active;
          return (
            <button
              key={tab.id}
              role="tab"
              aria-selected={selected}
              aria-controls={`panel-${tab.id}`}
              id={`tab-${tab.id}`}
              tabIndex={selected ? 0 : -1}
              onClick={() => select(tab.id)}
              onKeyDown={(e) => onKey(e, idx)}
              className={`rounded-md px-4 py-2 text-sm transition focus:outline-none focus-visible:ring-focus ${
                selected
                  ? "bg-accent-primary/30 text-white"
                  : "text-slate-400 hover:text-slate-200 hover:bg-white/[0.06]"
              }`}
            >
              {tab.label}
            </button>
          );
        })}
      </div>
      {tabs.map((tab) => (
        <div
          key={tab.id}
          id={`panel-${tab.id}`}
          role="tabpanel"
          aria-labelledby={`tab-${tab.id}`}
          hidden={tab.id !== active}
        >
          {tab.id === active && tab.panel}
        </div>
      ))}
    </div>
  );
}
