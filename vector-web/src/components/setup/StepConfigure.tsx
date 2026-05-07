"use client";

import { useState, useEffect } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { M } from "@/design/tokens";
import { Button } from "@/design/primitives";
import { Icon } from "@/design/primitives";
import { useDeployUrl } from "@/hooks/useDeployUrl";

export type Lang = "nextjs" | "node" | "springboot" | "python" | "go" | "custom";

const LANG_OPTIONS: { value: Lang; label: string }[] = [
  { value: "nextjs", label: "Next.js" },
  { value: "node", label: "Node" },
  { value: "springboot", label: "Spring Boot" },
  { value: "python", label: "Python" },
  { value: "go", label: "Go" },
  { value: "custom", label: "Custom" },
];

const APP_NAME_RE = /^[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}$/;

interface Props {
  lang: Lang;
  appName: string;
  branch: string;
  onLangChange: (v: Lang) => void;
  onAppNameChange: (v: string) => void;
  onBranchChange: (v: string) => void;
  onNext: () => void;
}

function CopyButton({ text, label }: { text: string; label: string }) {
  const [copied, setCopied] = useState(false);
  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch { /* noop */ }
  };
  return (
    <button
      type="button"
      onClick={handleCopy}
      aria-label={`Copy ${label}`}
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 5,
        padding: "4px 10px",
        borderRadius: M.rSm,
        border: `1px solid ${copied ? M.accentLine : M.line2}`,
        background: copied ? M.accentSoft : "transparent",
        color: copied ? M.accentLight : M.fg2,
        fontFamily: M.fontSans,
        fontSize: 11.5,
        fontWeight: 500,
        cursor: "pointer",
        whiteSpace: "nowrap",
        transition: "all 0.15s",
        flexShrink: 0,
      }}
    >
      <AnimatePresence mode="wait" initial={false}>
        {copied ? (
          <motion.span
            key="ok"
            initial={{ opacity: 0, scale: 0.85 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.85 }}
            transition={{ duration: 0.15 }}
            style={{ display: "inline-flex", alignItems: "center", gap: 5 }}
          >
            <Icon name="check" size={11} />
            Copied
          </motion.span>
        ) : (
          <motion.span
            key="copy"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
            style={{ display: "inline-flex", alignItems: "center", gap: 5 }}
          >
            <Icon name="copy" size={11} />
            Copy
          </motion.span>
        )}
      </AnimatePresence>
    </button>
  );
}

function SecretRow({
  name,
  value,
  masked,
  hint,
}: {
  name: string;
  value?: string;
  masked?: boolean;
  hint?: string;
}) {
  const [revealed, setRevealed] = useState(false);
  const display = masked && !revealed ? "•".repeat(24) : (value ?? "");

  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 10,
        padding: "11px 14px",
        background: M.surface2,
        border: `1px solid ${M.line}`,
        borderRadius: M.rMd,
      }}
    >
      <code
        style={{
          fontFamily: M.fontMono,
          fontSize: 12,
          color: M.accentLight,
          flexShrink: 0,
          letterSpacing: "0.01em",
        }}
      >
        {name}
      </code>
      <div
        style={{
          flex: 1,
          fontFamily: M.fontMono,
          fontSize: 12,
          color: M.fg2,
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap",
        }}
      >
        {hint ?? display}
      </div>
      {masked && value && (
        <button
          type="button"
          onClick={() => setRevealed((r) => !r)}
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 4,
            padding: "3px 8px",
            borderRadius: M.rSm,
            border: `1px solid ${M.line2}`,
            background: "transparent",
            color: M.fg3,
            fontSize: 11,
            fontFamily: M.fontSans,
            cursor: "pointer",
          }}
        >
          <Icon name={revealed ? "eye-off" : "eye"} size={11} />
        </button>
      )}
      {value && !hint && <CopyButton text={value} label={name} />}
    </div>
  );
}

export function StepConfigure({ lang, appName, branch, onLangChange, onAppNameChange, onBranchChange, onNext }: Props) {
  const { url } = useDeployUrl();
  const [hmacSecret, setHmacSecret] = useState<string>("");
  const [focusedField, setFocusedField] = useState<string | null>(null);

  // Load the HMAC secret from the deploy URL endpoint for display
  useEffect(() => {
    fetch("/api/setup/status")
      .then((r) => r.json())
      .then((data) => {
        if (data.secretConfigured) {
          setHmacSecret("(configured on server — set as VECTOR_HMAC_SECRET in GitHub)");
        }
      })
      .catch(() => {});
  }, []);

  const appNameValid = APP_NAME_RE.test(appName);
  const canProceed = appName.length > 0 && appNameValid;

  const inputStyle = (id: string) => ({
    width: "100%",
    padding: "9px 12px",
    borderRadius: M.rMd,
    border: `1px solid ${focusedField === id ? M.accentLine : M.line2}`,
    background: M.surface2,
    color: M.fg,
    fontSize: 13.5,
    fontFamily: M.fontSans,
    outline: "none",
    boxSizing: "border-box" as const,
    transition: "border-color 0.15s",
  });

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 28 }}>
      {/* Language */}
      <section>
        <div
          style={{
            fontSize: 11,
            fontWeight: 600,
            letterSpacing: "0.12em",
            textTransform: "uppercase",
            color: M.fg3,
            marginBottom: 12,
            fontFamily: M.fontSans,
          }}
        >
          Language
        </div>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
          {LANG_OPTIONS.map((opt) => {
            const sel = lang === opt.value;
            return (
              <button
                key={opt.value}
                type="button"
                onClick={() => onLangChange(opt.value)}
                style={{
                  padding: "7px 14px",
                  borderRadius: M.rPill,
                  border: `1px solid ${sel ? M.accentLine : M.line2}`,
                  background: sel ? M.accentSoft : "transparent",
                  color: sel ? M.accentLight : M.fg2,
                  fontSize: 13,
                  fontFamily: M.fontSans,
                  fontWeight: 500,
                  cursor: "pointer",
                  transition: "all 0.15s",
                }}
              >
                {opt.label}
              </button>
            );
          })}
        </div>
      </section>

      {/* App name + branch */}
      <section>
        <div
          style={{
            fontSize: 11,
            fontWeight: 600,
            letterSpacing: "0.12em",
            textTransform: "uppercase",
            color: M.fg3,
            marginBottom: 12,
            fontFamily: M.fontSans,
          }}
        >
          App settings
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr auto", gap: 12 }}>
          <div>
            <label
              htmlFor="setup-app-name"
              style={{
                display: "block",
                fontSize: 12,
                color: M.fg3,
                marginBottom: 6,
                fontFamily: M.fontSans,
              }}
            >
              App name
            </label>
            <input
              id="setup-app-name"
              value={appName}
              onChange={(e) => onAppNameChange(e.target.value)}
              onFocus={() => setFocusedField("app-name")}
              onBlur={() => setFocusedField(null)}
              placeholder="my-app"
              style={inputStyle("app-name")}
            />
            {appName.length > 0 && !appNameValid && (
              <div
                style={{
                  fontSize: 11.5,
                  color: M.err,
                  marginTop: 5,
                  fontFamily: M.fontSans,
                }}
              >
                Must start with a letter or digit; only letters, digits, . _ - allowed.
              </div>
            )}
          </div>

          <div>
            <label
              htmlFor="setup-branch"
              style={{
                display: "block",
                fontSize: 12,
                color: M.fg3,
                marginBottom: 6,
                fontFamily: M.fontSans,
              }}
            >
              Branch
            </label>
            <input
              id="setup-branch"
              value={branch}
              onChange={(e) => onBranchChange(e.target.value)}
              onFocus={() => setFocusedField("branch")}
              onBlur={() => setFocusedField(null)}
              style={{ ...inputStyle("branch"), width: 140 }}
            />
          </div>
        </div>
      </section>

      {/* Repo secrets */}
      <section>
        <div
          style={{
            fontSize: 11,
            fontWeight: 600,
            letterSpacing: "0.12em",
            textTransform: "uppercase",
            color: M.fg3,
            marginBottom: 6,
            fontFamily: M.fontSans,
          }}
        >
          Repo secrets
        </div>
        <div
          style={{
            fontSize: 12.5,
            color: M.fg3,
            marginBottom: 12,
            fontFamily: M.fontSans,
            lineHeight: 1.5,
          }}
        >
          Add these as GitHub Actions secrets in your repo under Settings → Secrets → Actions.
        </div>
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          <SecretRow
            name="VECTOR_DEPLOY_URL"
            value={url || undefined}
          />
          <SecretRow
            name="VECTOR_HMAC_SECRET"
            value={hmacSecret || "configured on server"}
            masked
          />
          <SecretRow
            name="DOCKERHUB_USERNAME"
            hint="your DockerHub username"
          />
          <SecretRow
            name="DOCKERHUB_TOKEN"
            hint="your DockerHub access token"
          />
        </div>
      </section>

      {/* Next button */}
      <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 4 }}>
        <Button
          variant="primary"
          size="md"
          trailingIcon="arrow-right"
          disabled={!canProceed}
          onClick={onNext}
        >
          Next
        </Button>
      </div>
    </div>
  );
}
