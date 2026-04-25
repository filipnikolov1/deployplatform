"use client";

import { X } from "lucide-react";
import { usePreferences } from "@/hooks/usePreferences";
import { useToast } from "@/hooks/useToast";
import type { UserPreferences } from "@/types/vector";

function Toggle({
  checked,
  onChange,
  label,
  description,
}: {
  checked: boolean;
  onChange: (next: boolean) => void;
  label: string;
  description?: string;
}) {
  return (
    <label
      className="flex items-start justify-between gap-4 py-3.5 cursor-pointer"
      style={{ borderBottom: "1px solid var(--c-border-1)" }}
    >
      <div className="flex-1">
        <div className="text-sm font-medium" style={{ color: "var(--c-fg-1)" }}>{label}</div>
        {description && (
          <div className="text-xs mt-0.5" style={{ color: "var(--c-fg-3)" }}>{description}</div>
        )}
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        onClick={() => onChange(!checked)}
        className="relative inline-flex h-[22px] w-10 shrink-0 rounded-full transition-all duration-[150ms] focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-light mt-0.5"
        style={{
          background: checked ? "var(--c-accent-soft)" : "var(--c-surface-1)",
          border: `1px solid ${checked ? "var(--c-accent-line)" : "var(--c-border-2)"}`,
        }}
      >
        <span
          aria-hidden="true"
          className="inline-block h-4 w-4 rounded-full bg-white shadow transition-[left] duration-[150ms] ease-out absolute top-[2px]"
          style={{ left: checked ? 20 : 2, boxShadow: "0 1px 3px rgba(0,0,0,0.3)" }}
        />
      </button>
    </label>
  );
}

function RadioRow<T extends string>({
  value,
  onChange,
  options,
  name,
}: {
  value: T;
  onChange: (next: T) => void;
  options: { value: T; label: string }[];
  name: string;
}) {
  return (
    <div role="radiogroup" className="flex gap-1.5 flex-wrap">
      {options.map((opt) => {
        const sel = opt.value === value;
        return (
          <button
            key={opt.value}
            type="button"
            role="radio"
            aria-checked={sel}
            onClick={() => onChange(opt.value)}
            data-group={name}
            className="px-3.5 py-1.5 rounded-full text-[13px] font-medium transition-all duration-[120ms] focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-light"
            style={{
              background: sel ? "var(--c-accent-soft)" : "var(--c-surface-1)",
              border: `1px solid ${sel ? "var(--c-accent-line)" : "var(--c-border-2)"}`,
              color: sel ? "var(--c-accent-fg)" : "var(--c-fg-2)",
              cursor: "pointer",
            }}
          >
            {opt.label}
          </button>
        );
      })}
    </div>
  );
}

function SettingsCard({ title, description, children }: { title: string; description?: string; children: React.ReactNode }) {
  return (
    <section
      className="rounded-[14px] p-6 mb-4"
      style={{
        background: "var(--c-surface-1)",
        border: "1px solid var(--c-border-1)",
      }}
    >
      <h2 className="text-[15px] font-semibold tracking-[-0.005em] m-0" style={{ color: "var(--c-fg-0)" }}>
        {title}
      </h2>
      {description && (
        <p className="text-[13px] mt-1 mb-4" style={{ color: "var(--c-fg-2)" }}>{description}</p>
      )}
      {children}
    </section>
  );
}

export function PreferencesForm() {
  const { prefs, update } = usePreferences();
  const toast = useToast();

  const save = async (patch: Partial<UserPreferences>) => {
    try {
      await update(patch);
      toast.success("Saved");
    } catch {
      toast.error("Failed to save");
    }
  };

  return (
    <div className="flex flex-col">
      <SettingsCard
        title="Email notifications"
        description="Get emailed when important events happen."
      >
        <div style={{ borderTop: "1px solid var(--c-border-1)" }}>
          {[
            ["notify_on_fail", "Deploy failed", "Notify me when a deploy fails to build or start."],
            ["notify_on_first_deploy", "First deploy", "Notify me the first time a new app deploys successfully."],
            ["notify_on_crash", "Container crashed", "Notify me when a running container exits unexpectedly."],
            ["notify_on_rollback", "Rollback performed", "Notify me when an app is manually rolled back."],
            ["notify_on_update_available", "Update available", "Email me when a self-app update is available."],
          ].map(([key, label, desc]) => (
            <Toggle
              key={key}
              label={label}
              description={desc}
              checked={prefs[key as keyof typeof prefs] as boolean}
              onChange={(v) => save({ [key]: v })}
            />
          ))}
          {/* Remove border from last item */}
          <style>{`label:last-child { border-bottom: none !important; }`}</style>
        </div>
      </SettingsCard>

      <SettingsCard
        title="Appearance"
        description="How the dashboard renders and animates."
      >
        <div className="flex flex-col gap-5">
          <div>
            <div className="text-[13px] font-medium mb-2" style={{ color: "var(--c-fg-1)" }}>Layout mode</div>
            <RadioRow
              name="layout_mode"
              value={prefs.layout_mode}
              onChange={(v) => save({ layout_mode: v })}
              options={[
                { value: "grid", label: "Grid" },
                { value: "list", label: "List" },
              ]}
            />
          </div>
          <div>
            <div className="text-[13px] font-medium mb-2" style={{ color: "var(--c-fg-1)" }}>Reduced motion</div>
            <RadioRow
              name="reduced_motion"
              value={prefs.reduced_motion}
              onChange={(v) => save({ reduced_motion: v })}
              options={[
                { value: "system", label: "System" },
                { value: "always", label: "Always" },
                { value: "never", label: "Never" },
              ]}
            />
          </div>
        </div>
      </SettingsCard>

      <SettingsCard
        title="Pinned apps"
        description="These apps stay at the top of your dashboard."
      >
        {prefs.pinned_apps.length === 0 ? (
          <p className="text-[13px] m-0" style={{ color: "var(--c-fg-3)" }}>No pinned apps.</p>
        ) : (
          <ul className="flex flex-col gap-1.5 list-none p-0 m-0">
            {prefs.pinned_apps.map((name) => (
              <li
                key={name}
                className="flex items-center justify-between px-3 py-2.5 rounded-md"
                style={{
                  background: "var(--c-surface-2)",
                  border: "1px solid var(--c-border-1)",
                }}
              >
                <span className="font-mono text-[13px]" style={{ color: "var(--c-fg-1)" }}>{name}</span>
                <button
                  type="button"
                  aria-label={`Unpin ${name}`}
                  onClick={() => save({ pinned_apps: prefs.pinned_apps.filter((n) => n !== name) })}
                  className="h-7 w-7 rounded flex items-center justify-center transition-colors duration-[120ms] focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-light"
                  style={{ color: "var(--c-fg-3)", background: "transparent", border: "none", cursor: "pointer" }}
                  onMouseEnter={(e) => { e.currentTarget.style.background = "var(--c-surface-3)"; e.currentTarget.style.color = "var(--c-fg-1)"; }}
                  onMouseLeave={(e) => { e.currentTarget.style.background = "transparent"; e.currentTarget.style.color = "var(--c-fg-3)"; }}
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              </li>
            ))}
          </ul>
        )}
      </SettingsCard>

      <SettingsCard
        title="Danger zone"
        description="Irreversible account actions."
      >
        <div className="flex gap-2">
          <a
            href="/api/auth/logout"
            className="inline-flex items-center gap-2 rounded-md px-3.5 py-1.5 text-[13px] font-medium transition-colors duration-[120ms] outline-none focus-visible:ring-2 focus-visible:ring-accent-light"
            style={{
              background: "rgba(239,68,68,0.10)",
              color: "#FCA5A5",
              border: "1px solid rgba(248,113,113,0.28)",
              textDecoration: "none",
            }}
          >
            Sign out
          </a>
        </div>
      </SettingsCard>
    </div>
  );
}
