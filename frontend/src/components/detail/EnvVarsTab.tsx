"use client";

import { useState } from "react";
import useSWR from "swr";
import { Trash2, Plus } from "lucide-react";
import { Input } from "@/components/primitives/Input";
import { Button } from "@/components/primitives/Button";
import { useToast } from "@/hooks/useToast";

interface Props {
  appName: string;
}

const fetcher = async (url: string): Promise<Record<string, string>> => {
  const res = await fetch(url);
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
    if (!key || busy) return;
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
        toast.error(`Failed to set ${key}`);
        return;
      }
      toast.success(`${key} saved`);
      void mutate();
    } catch {
      toast.error(`Failed to set ${key}`);
    } finally {
      setBusy(false);
    }
  };

  const handleDelete = async (key: string) => {
    if (busy) return;
    setBusy(true);
    try {
      const res = await fetch(
        `/api/apps/${encodeURIComponent(appName)}/env/${encodeURIComponent(key)}`,
        { method: "DELETE" },
      );
      if (!res.ok) {
        toast.error(`Failed to delete ${key}`);
        return;
      }
      toast.success(`${key} deleted`);
      void mutate();
    } catch {
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
        <div className="text-sm text-slate-400">No env vars set.</div>
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
      <div className="pt-3 border-t border-white/[0.08] space-y-2">
        <div className="grid grid-cols-2 gap-2">
          <Input
            label="Key"
            value={newKey}
            onChange={(e) => setNewKey(e.target.value)}
            placeholder="DATABASE_URL"
          />
          <Input
            label="Value"
            value={newValue}
            onChange={(e) => setNewValue(e.target.value)}
            placeholder="postgres://…"
          />
        </div>
        <Button
          leadingIcon={<Plus className="h-4 w-4" />}
          onClick={handleAdd}
          disabled={busy || !newKey}
        >
          Add variable
        </Button>
      </div>
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

function EnvVarRow({ varKey, initialValue, onSave, onDelete, disabled }: RowProps) {
  const [value, setValue] = useState(initialValue);
  const dirty = value !== initialValue;

  return (
    <li className="flex items-center gap-2">
      <div className="flex-1 min-w-0 grid grid-cols-2 gap-2">
        <div className="font-mono text-xs text-slate-200 truncate">{varKey}</div>
        <input
          className="bg-white/[0.04] border border-white/[0.08] rounded px-2 py-1 font-mono text-xs text-slate-200 focus:outline-none focus:ring-1 focus:ring-purple-400"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          disabled={disabled}
        />
      </div>
      <button
        type="button"
        className="text-xs text-purple-300 hover:text-purple-200 disabled:opacity-60"
        onClick={() => onSave(value)}
        disabled={disabled || !dirty}
      >
        Save
      </button>
      <button
        type="button"
        className="text-slate-400 hover:text-red-300 disabled:opacity-60"
        onClick={onDelete}
        disabled={disabled}
        aria-label={`Delete ${varKey}`}
      >
        <Trash2 className="h-4 w-4" />
      </button>
    </li>
  );
}
