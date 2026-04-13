"use client";

import { X } from "lucide-react";
import { GlassCard } from "@/components/primitives/GlassCard";
import { usePreferences } from "@/hooks/usePreferences";
import { useToast } from "@/hooks/useToast";
import type { UserPreferences } from "@/types/launchpad";

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
    <label className="flex items-start justify-between gap-4 py-3 cursor-pointer">
      <div className="flex-1">
        <div className="text-sm font-medium text-slate-100">{label}</div>
        {description && (
          <div className="text-xs text-slate-400 mt-0.5">{description}</div>
        )}
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        onClick={() => onChange(!checked)}
        className={`relative inline-flex h-6 w-11 shrink-0 rounded-full border transition-colors focus:outline-none focus-visible:ring-focus ${
          checked
            ? "bg-accent-ghost/40 border-accent-ghostLight"
            : "bg-white/[0.04] border-white/10"
        }`}
      >
        <span
          aria-hidden="true"
          className={`inline-block h-5 w-5 rounded-full bg-white shadow transition-transform ${
            checked ? "translate-x-5" : "translate-x-0.5"
          } translate-y-0.5`}
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
    <div role="radiogroup" className="flex gap-2 flex-wrap">
      {options.map((opt) => {
        const selected = opt.value === value;
        return (
          <button
            key={opt.value}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={() => onChange(opt.value)}
            className={`px-3 py-1.5 rounded-full text-sm border transition-colors focus:outline-none focus-visible:ring-focus ${
              selected
                ? "bg-accent-ghost/30 border-accent-ghostLight text-white"
                : "bg-white/[0.04] border-white/10 text-slate-300 hover:bg-white/[0.08]"
            }`}
            data-group={name}
          >
            {opt.label}
          </button>
        );
      })}
    </div>
  );
}

export function PreferencesForm() {
  const { prefs, update, email } = usePreferences();
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
    <div className="flex flex-col gap-6">
      <GlassCard radius="panel" className="p-6">
        <h2 className="text-lg font-semibold text-slate-100 mb-1">
          Email notifications
        </h2>
        <p className="text-sm text-slate-400 mb-2">
          Get emailed when important events happen.
        </p>
        <div className="divide-y divide-white/5">
          <Toggle
            label="Deploy failed"
            description="Notify me when a deploy fails to build or start."
            checked={prefs.notify_on_fail}
            onChange={(v) => save({ notify_on_fail: v })}
          />
          <Toggle
            label="First deploy"
            description="Notify me the first time a new app deploys successfully."
            checked={prefs.notify_on_first_deploy}
            onChange={(v) => save({ notify_on_first_deploy: v })}
          />
          <Toggle
            label="Container crashed"
            description="Notify me when a running container exits unexpectedly."
            checked={prefs.notify_on_crash}
            onChange={(v) => save({ notify_on_crash: v })}
          />
          <Toggle
            label="Rollback performed"
            description="Notify me when an app is manually rolled back."
            checked={prefs.notify_on_rollback}
            onChange={(v) => save({ notify_on_rollback: v })}
          />
        </div>
      </GlassCard>

      <GlassCard radius="panel" className="p-6">
        <h2 className="text-lg font-semibold text-slate-100 mb-1">
          Appearance
        </h2>
        <p className="text-sm text-slate-400 mb-4">
          How the dashboard renders and animates.
        </p>
        <div className="space-y-5">
          <div>
            <div className="text-sm font-medium text-slate-200 mb-2">
              Layout mode
            </div>
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
            <div className="text-sm font-medium text-slate-200 mb-2">
              Reduced motion
            </div>
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
      </GlassCard>

      <GlassCard radius="panel" className="p-6">
        <h2 className="text-lg font-semibold text-slate-100 mb-1">
          Pinned apps
        </h2>
        <p className="text-sm text-slate-400 mb-4">
          These apps stay at the top of your dashboard.
        </p>
        {prefs.pinned_apps.length === 0 ? (
          <p className="text-sm text-slate-400">No pinned apps.</p>
        ) : (
          <ul className="space-y-2">
            {prefs.pinned_apps.map((name) => (
              <li
                key={name}
                className="flex items-center justify-between p-3 rounded-lg bg-white/[0.04] border border-white/[0.08]"
              >
                <span className="font-mono text-sm text-slate-200">
                  {name}
                </span>
                <button
                  type="button"
                  aria-label={`Unpin ${name}`}
                  onClick={() =>
                    save({
                      pinned_apps: prefs.pinned_apps.filter((n) => n !== name),
                    })
                  }
                  className="h-8 w-8 rounded-lg hover:bg-white/10 flex items-center justify-center text-slate-400 hover:text-slate-200 focus:outline-none focus-visible:ring-focus"
                >
                  <X className="h-4 w-4" />
                </button>
              </li>
            ))}
          </ul>
        )}
      </GlassCard>

      <GlassCard radius="panel" className="p-6">
        <h2 className="text-lg font-semibold text-slate-100 mb-1">Account</h2>
        <p className="text-sm text-slate-400 mb-4">
          Your Launchpad account details.
        </p>
        <div className="space-y-3">
          <div>
            <div className="text-xs text-slate-400 mb-1">Email</div>
            <div className="font-mono text-sm text-slate-200">{email}</div>
          </div>
          <div>
            <a
              href="/README.md#api-key"
              className="text-sm text-accent-ghostLight hover:text-white transition-colors focus:outline-none focus-visible:ring-focus"
            >
              Regenerate API key (see README)
            </a>
          </div>
        </div>
      </GlassCard>
    </div>
  );
}
