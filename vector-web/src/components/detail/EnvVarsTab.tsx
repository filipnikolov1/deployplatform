"use client";

import { useEffect, useState } from "react";
import useSWR from "swr";
import { Eye, EyeOff, KeyRound, Plus, Trash2 } from "lucide-react";
import { useToast } from "@/hooks/useToast";

interface Props {
  appName: string;
}

const fetcher = async (url: string): Promise<Record<string, string>> => {
  const res = await fetch(url, { signal: AbortSignal.timeout(15_000) });
  if (!res.ok) throw new Error(`Failed: ${res.status}`);
  return res.json();
};

export function EnvVarsTab({ appName }: Props) {
  const endpoint = `/api/apps/${encodeURIComponent(appName)}/env`;
  const { data, mutate, isLoading, error } = useSWR<Record<string, string>>(
    endpoint,
    fetcher,
  );
  const toast = useToast();
  const [newKey, setNewKey] = useState("");
  const [newValue, setNewValue] = useState("");
  const [busy, setBusy] = useState(false);

  const handleSet = async (key: string, value: string) => {
    if (!key) return;
    setBusy(true);
    try {
      const res = await fetch(
        `/api/apps/${encodeURIComponent(appName)}/env/${encodeURIComponent(key)}`,
        {
          method: "PUT",
          headers: { "Content-Type": "text/plain" },
          body: value,
        },
      );
      if (!res.ok) {
        const detail = await res.text().catch(() => "");
        console.error("env var save failed", res.status, detail);
        toast.error(`Failed to set ${key} (${res.status})`);
        return;
      }
      toast.success(`${key} saved`);
      void mutate();
    } catch (err) {
      console.error("env var save error", err);
      toast.error(`Failed to set ${key}`);
    } finally {
      setBusy(false);
    }
  };

  const handleDelete = async (key: string) => {
    setBusy(true);
    try {
      const res = await fetch(
        `/api/apps/${encodeURIComponent(appName)}/env/${encodeURIComponent(key)}`,
        { method: "DELETE" },
      );
      if (!res.ok) {
        const detail = await res.text().catch(() => "");
        console.error("env var delete failed", res.status, detail);
        toast.error(`Failed to delete ${key} (${res.status})`);
        return;
      }
      toast.success(`${key} deleted`);
      void mutate();
    } catch (err) {
      console.error("env var delete error", err);
      toast.error(`Failed to delete ${key}`);
    } finally {
      setBusy(false);
    }
  };

  const handleAdd = async () => {
    if (!newKey) return;
    await handleSet(newKey, newValue);
    setNewKey("");
    setNewValue("");
  };

  if (isLoading) {
    return <div className="text-sm text-slate-400">Loading env vars…</div>;
  }
  if (error) {
    return <div className="text-sm text-red-300">Failed to load env vars.</div>;
  }

  const entries = Object.entries(data ?? {});

  return (
    <div className="space-y-3 max-h-[400px] overflow-auto">
      {entries.length === 0 ? (
        <div className="flex flex-col items-center gap-3 rounded-lg border border-white/[0.06] bg-white/[0.03] p-6 text-center">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-white/[0.08] bg-white/[0.04]">
            <KeyRound className="h-5 w-5 text-slate-400" />
          </div>
          <div>
            <div className="text-sm font-medium text-slate-200">
              No environment variables yet
            </div>
            <div className="mt-1 text-xs text-slate-400">
              Add one below to expose it to your container on next deploy.
            </div>
          </div>
        </div>
      ) : (
        <ul className="space-y-2">
          {entries.map(([key, value]) => (
            <EnvVarRow
              key={key}
              varKey={key}
              initialValue={value}
              onSave={(v) => handleSet(key, v)}
              onDelete={() => handleDelete(key)}
              disabled={busy}
            />
          ))}
        </ul>
      )}

      {/* Add variable row */}
      <div className="flex items-center gap-3 rounded-lg border border-dashed border-white/[0.10] bg-white/[0.02] px-3 py-2">
        <input
          className="w-[180px] shrink-0 bg-transparent font-mono text-xs text-slate-200 placeholder:text-slate-500 focus:outline-none"
          value={newKey}
          onChange={(e) =>
            setNewKey(
              e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, ""),
            )
          }
          placeholder="KEY_NAME"
          disabled={busy}
        />
        <input
          className="flex-1 bg-transparent font-mono text-xs text-slate-200 placeholder:text-slate-500 focus:outline-none"
          type="text"
          value={newValue}
          onChange={(e) => setNewValue(e.target.value)}
          placeholder="value"
          disabled={busy}
        />
        <button
          type="button"
          onClick={handleAdd}
          disabled={busy || !newKey}
          className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md text-purple-300 hover:bg-white/[0.06] hover:text-purple-200 disabled:opacity-40"
          aria-label="Add variable"
        >
          <Plus className="h-4 w-4" />
        </button>
      </div>

      {/* Restart hint */}
      <p className="text-[11px] uppercase tracking-[0.14em] text-slate-500 text-center">
        Changes apply on next deploy or restart.
      </p>
    </div>
  );
}

interface RowProps {
  varKey: string;
  initialValue: string;
  onSave: (value: string) => Promise<void> | void;
  onDelete: () => Promise<void> | void;
  disabled: boolean;
}

function EnvVarRow({
  varKey,
  initialValue,
  onSave,
  onDelete,
  disabled,
}: RowProps) {
  const [value, setValue] = useState(initialValue);
  const [revealed, setRevealed] = useState(false);

  useEffect(() => {
    setValue(initialValue);
  }, [initialValue]);

  const dirty = value !== initialValue;

  return (
    <li className="flex items-center gap-3 rounded-lg border border-white/[0.06] bg-white/[0.02] px-3 py-2 transition-colors hover:border-white/[0.10] hover:bg-white/[0.04]">
      <div className="w-[180px] shrink-0 truncate font-mono text-xs text-slate-200">
        {varKey}
      </div>
      <div className="relative flex-1">
        <input
          type={revealed ? "text" : "password"}
          className="w-full bg-transparent font-mono text-xs text-slate-200 focus:outline-none"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          disabled={disabled}
        />
      </div>
      <button
        type="button"
        onClick={() => setRevealed((r) => !r)}
        className="text-slate-400 hover:text-slate-200"
        aria-label={revealed ? "Hide value" : "Reveal value"}
      >
        {revealed ? (
          <EyeOff className="h-4 w-4" />
        ) : (
          <Eye className="h-4 w-4" />
        )}
      </button>
      <button
        type="button"
        onClick={() => onSave(value)}
        disabled={!dirty || disabled}
        className="text-xs font-medium text-purple-300 hover:text-purple-200 disabled:opacity-40"
      >
        Save
      </button>
      <button
        type="button"
        onClick={onDelete}
        disabled={disabled}
        className="text-slate-400 hover:text-red-300 disabled:opacity-60"
        aria-label={`Delete ${varKey}`}
      >
        <Trash2 className="h-4 w-4" />
      </button>
    </li>
  );
}
