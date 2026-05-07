"use client";

/**
 * EnvVarsTab — F5.4 (restyled, same functionality)
 * Mono labels, pill buttons, show/hide switch, copy-morphs-to-Copied row actions.
 */

import { useEffect, useRef, useState } from "react";
import useSWR from "swr";
import { KeyRound, Plus, Trash2 } from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";
import { M } from "@/design/tokens";
import { Button } from "@/design/primitives/Button";
import { toast } from "@/lib/toast";

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
  const { data, mutate, isLoading, error } = useSWR<Record<string, string>>(endpoint, fetcher);
  const [newKey, setNewKey] = useState("");
  const [newValue, setNewValue] = useState("");
  const [busy, setBusy] = useState(false);
  const [showAll, setShowAll] = useState(false);

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
        }
      );
      if (!res.ok) {
        toast.error(`Failed to set ${key} (${res.status})`);
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
    setBusy(true);
    try {
      const res = await fetch(
        `/api/apps/${encodeURIComponent(appName)}/env/${encodeURIComponent(key)}`,
        { method: "DELETE" }
      );
      if (!res.ok) {
        toast.error(`Failed to delete ${key} (${res.status})`);
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
    return (
      <div style={{ color: M.fg3, fontSize: 13, fontFamily: M.fontSans }}>
        Loading env vars…
      </div>
    );
  }
  if (error) {
    return (
      <div style={{ color: M.err, fontSize: 13, fontFamily: M.fontSans }}>
        Failed to load env vars.
      </div>
    );
  }

  const entries = Object.entries(data ?? {});

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
      {/* Show/hide toggle */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
        }}
      >
        <span
          style={{
            fontFamily: M.fontMono,
            fontSize: 10,
            fontWeight: 700,
            letterSpacing: "0.12em",
            textTransform: "uppercase",
            color: M.fg3,
          }}
        >
          {entries.length} variable{entries.length !== 1 ? "s" : ""}
        </span>
        <label
          style={{
            display: "flex",
            alignItems: "center",
            gap: 7,
            cursor: "pointer",
            fontSize: 11.5,
            fontFamily: M.fontSans,
            color: M.fg2,
            userSelect: "none",
          }}
        >
          <button
            type="button"
            role="switch"
            aria-checked={showAll}
            onClick={() => setShowAll((v) => !v)}
            style={{
              width: 28,
              height: 16,
              borderRadius: M.rPill,
              background: showAll ? M.accent : M.surface2,
              border: `1px solid ${showAll ? M.accentLine : M.line2}`,
              cursor: "pointer",
              position: "relative",
              outline: "none",
              flexShrink: 0,
              transition: "background 150ms",
            }}
          >
            <motion.span
              animate={{ x: showAll ? 13 : 1 }}
              transition={{ type: "spring", stiffness: 380, damping: 32 }}
              style={{
                display: "block",
                width: 12,
                height: 12,
                borderRadius: "50%",
                background: M.fg,
                position: "absolute",
                top: 1,
              }}
            />
          </button>
          Show values
        </label>
      </div>

      {/* Variables list */}
      {entries.length === 0 ? (
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            gap: 12,
            padding: "28px 20px",
            textAlign: "center",
            border: `1px solid ${M.line}`,
            borderRadius: M.rMd,
            background: M.surface,
          }}
        >
          <div
            style={{
              width: 36,
              height: 36,
              borderRadius: M.rMd,
              border: `1px solid ${M.line2}`,
              background: M.surface2,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              color: M.fg3,
            }}
          >
            <KeyRound style={{ width: 16, height: 16 }} />
          </div>
          <div>
            <div
              style={{
                fontSize: 13,
                fontWeight: 500,
                color: M.fg,
                fontFamily: M.fontSans,
              }}
            >
              No environment variables
            </div>
            <div
              style={{
                marginTop: 4,
                fontSize: 12,
                color: M.fg3,
                fontFamily: M.fontSans,
              }}
            >
              Add one below to expose it on next deploy.
            </div>
          </div>
        </div>
      ) : (
        <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "flex", flexDirection: "column", gap: 6 }}>
          {entries.map(([key, value]) => (
            <EnvVarRow
              key={key}
              varKey={key}
              initialValue={value}
              revealed={showAll}
              onSave={(v) => handleSet(key, v)}
              onDelete={() => handleDelete(key)}
              disabled={busy}
            />
          ))}
        </ul>
      )}

      {/* Add row */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 8,
          padding: "8px 12px",
          border: `1px dashed ${M.line2}`,
          borderRadius: M.rMd,
          background: M.surface,
        }}
      >
        <input
          style={{
            width: 160,
            flexShrink: 0,
            background: "transparent",
            border: "none",
            outline: "none",
            fontFamily: M.fontMono,
            fontSize: 12,
            color: M.fg,
          }}
          value={newKey}
          onChange={(e) => setNewKey(e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, ""))}
          placeholder="KEY_NAME"
          disabled={busy}
        />
        <div
          style={{
            width: 1,
            height: 16,
            background: M.line,
            flexShrink: 0,
          }}
        />
        <input
          style={{
            flex: 1,
            background: "transparent",
            border: "none",
            outline: "none",
            fontFamily: M.fontMono,
            fontSize: 12,
            color: M.fg,
          }}
          type={showAll ? "text" : "password"}
          value={newValue}
          onChange={(e) => setNewValue(e.target.value)}
          placeholder="value"
          disabled={busy}
          onKeyDown={(e) => {
            if (e.key === "Enter") void handleAdd();
          }}
        />
        <button
          type="button"
          onClick={handleAdd}
          disabled={busy || !newKey}
          aria-label="Add variable"
          style={{
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
            width: 26,
            height: 26,
            borderRadius: M.rSm,
            background: M.accentSoft,
            border: `1px solid ${M.accentLine}`,
            color: M.accent,
            cursor: busy || !newKey ? "not-allowed" : "pointer",
            opacity: busy || !newKey ? 0.5 : 1,
            outline: "none",
            flexShrink: 0,
          }}
        >
          <Plus style={{ width: 13, height: 13 }} />
        </button>
      </div>

      <p
        style={{
          fontSize: 11,
          fontFamily: M.fontMono,
          textTransform: "uppercase",
          letterSpacing: "0.12em",
          color: M.fg4,
          textAlign: "center",
          margin: 0,
        }}
      >
        Changes apply on next deploy or restart
      </p>
    </div>
  );
}

interface RowProps {
  varKey: string;
  initialValue: string;
  revealed: boolean;
  onSave: (value: string) => Promise<void> | void;
  onDelete: () => Promise<void> | void;
  disabled: boolean;
}

function EnvVarRow({ varKey, initialValue, revealed, onSave, onDelete, disabled }: RowProps) {
  const [value, setValue] = useState(initialValue);
  const [copied, setCopied] = useState(false);
  const copyTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    setValue(initialValue);
  }, [initialValue]);

  const dirty = value !== initialValue;

  const handleCopy = () => {
    void navigator.clipboard.writeText(value);
    setCopied(true);
    if (copyTimerRef.current) clearTimeout(copyTimerRef.current);
    copyTimerRef.current = setTimeout(() => setCopied(false), 2000);
  };

  return (
    <li
      style={{
        display: "flex",
        alignItems: "center",
        gap: 8,
        padding: "7px 12px",
        border: `1px solid ${M.line}`,
        borderRadius: M.rMd,
        background: M.surface,
        transition: "border-color 150ms",
      }}
      onMouseEnter={(e) => {
        e.currentTarget.style.borderColor = M.line2;
      }}
      onMouseLeave={(e) => {
        e.currentTarget.style.borderColor = M.line;
      }}
    >
      {/* Key */}
      <span
        style={{
          width: 160,
          flexShrink: 0,
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap",
          fontFamily: M.fontMono,
          fontSize: 12,
          fontWeight: 600,
          color: M.fg,
        }}
      >
        {varKey}
      </span>

      {/* Value */}
      <div style={{ flex: 1, minWidth: 0 }}>
        <input
          type={revealed ? "text" : "password"}
          value={value}
          onChange={(e) => setValue(e.target.value)}
          disabled={disabled}
          style={{
            width: "100%",
            background: "transparent",
            border: "none",
            outline: "none",
            fontFamily: M.fontMono,
            fontSize: 12,
            color: M.fg2,
          }}
        />
      </div>

      {/* Copy button */}
      <button
        type="button"
        onClick={handleCopy}
        aria-label={copied ? "Copied" : `Copy ${varKey}`}
        style={{
          flexShrink: 0,
          display: "inline-flex",
          alignItems: "center",
          gap: 4,
          padding: "3px 8px",
          borderRadius: M.rPill,
          fontSize: 10.5,
          fontFamily: M.fontSans,
          fontWeight: 500,
          border: `1px solid ${copied ? M.accentLine : M.line}`,
          background: copied ? M.accentSoft : "transparent",
          color: copied ? M.accentLight : M.fg3,
          cursor: "pointer",
          outline: "none",
          transition: "all 200ms",
          whiteSpace: "nowrap",
        }}
      >
        <AnimatePresence mode="wait">
          <motion.span
            key={copied ? "copied" : "copy"}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.12 }}
          >
            {copied ? "Copied" : "Copy"}
          </motion.span>
        </AnimatePresence>
      </button>

      {/* Save */}
      {dirty && (
        <button
          type="button"
          onClick={() => onSave(value)}
          disabled={disabled}
          style={{
            flexShrink: 0,
            padding: "3px 10px",
            borderRadius: M.rPill,
            fontSize: 11,
            fontFamily: M.fontSans,
            fontWeight: 500,
            border: `1px solid ${M.accentLine}`,
            background: M.accentSoft,
            color: M.accentLight,
            cursor: disabled ? "not-allowed" : "pointer",
            opacity: disabled ? 0.5 : 1,
            outline: "none",
          }}
        >
          Save
        </button>
      )}

      {/* Delete */}
      <button
        type="button"
        onClick={onDelete}
        disabled={disabled}
        aria-label={`Delete ${varKey}`}
        style={{
          flexShrink: 0,
          display: "inline-flex",
          alignItems: "center",
          justifyContent: "center",
          padding: 4,
          borderRadius: M.rSm,
          background: "transparent",
          border: "none",
          color: M.fg3,
          cursor: disabled ? "not-allowed" : "pointer",
          opacity: disabled ? 0.5 : 1,
          outline: "none",
          transition: "color 150ms",
        }}
        onMouseEnter={(e) => { e.currentTarget.style.color = M.err; }}
        onMouseLeave={(e) => { e.currentTarget.style.color = M.fg3; }}
      >
        <Trash2 style={{ width: 13, height: 13 }} />
      </button>
    </li>
  );
}

