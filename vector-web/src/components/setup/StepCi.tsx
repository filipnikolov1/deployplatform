"use client";

import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import useSWR from "swr";
import SyntaxHighlighter from "react-syntax-highlighter";
import { atomOneDark } from "react-syntax-highlighter/dist/esm/styles/hljs";
import { M } from "@/design/tokens";
import { Button, Icon } from "@/design/primitives";
import type { Lang } from "./StepConfigure";

interface Props {
  lang: Lang;
  appName: string;
  branch: string;
  onBack: () => void;
  onNext: () => void;
}

const fetcher = async (url: string): Promise<string> => {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Failed to load CI template: ${res.status}`);
  return res.text();
};

function CopyButton({ text }: { text: string }) {
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
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 6,
        padding: "5px 12px",
        borderRadius: M.rSm,
        border: `1px solid ${copied ? M.accentLine : M.line2}`,
        background: copied ? M.accentSoft : M.surface2,
        color: copied ? M.accentLight : M.fg2,
        fontFamily: M.fontSans,
        fontSize: 12,
        fontWeight: 500,
        cursor: "pointer",
        transition: "all 0.15s",
      }}
    >
      <AnimatePresence mode="wait" initial={false}>
        {copied ? (
          <motion.span
            key="ok"
            initial={{ opacity: 0, scale: 0.85 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
            style={{ display: "inline-flex", alignItems: "center", gap: 6 }}
          >
            <Icon name="check" size={12} />
            Copied
          </motion.span>
        ) : (
          <motion.span
            key="copy"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
            style={{ display: "inline-flex", alignItems: "center", gap: 6 }}
          >
            <Icon name="copy" size={12} />
            Copy
          </motion.span>
        )}
      </AnimatePresence>
    </button>
  );
}

function downloadFile(filename: string, content: string) {
  const blob = new Blob([content], { type: "text/yaml" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

export function StepCi({ lang, appName, branch, onBack, onNext }: Props) {
  const key = `/api/setup/templates/ci?lang=${lang}&app=${encodeURIComponent(appName)}&branch=${encodeURIComponent(branch)}`;
  const { data: yaml, isLoading, error } = useSWR<string>(key, fetcher, {
    revalidateOnFocus: false,
    keepPreviousData: true,
  });

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
      <div>
        <div
          style={{
            fontSize: 13,
            color: M.fg2,
            marginBottom: 16,
            fontFamily: M.fontSans,
            lineHeight: 1.55,
          }}
        >
          Save this as{" "}
          <code
            style={{
              fontFamily: M.fontMono,
              fontSize: 12,
              color: M.accentLight,
              background: M.accentSoft,
              padding: "2px 6px",
              borderRadius: M.rSm,
            }}
          >
            .github/workflows/deploy.yml
          </code>{" "}
          in your repo.
        </div>

        {/* Toolbar */}
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            marginBottom: 8,
          }}
        >
          <span
            style={{
              fontSize: 11,
              fontWeight: 600,
              letterSpacing: "0.12em",
              textTransform: "uppercase",
              color: M.fg3,
              fontFamily: M.fontSans,
            }}
          >
            deploy.yml
          </span>
          <div style={{ display: "flex", gap: 8 }}>
            {yaml && <CopyButton text={yaml} />}
            <button
              type="button"
              disabled={!yaml}
              onClick={() => yaml && downloadFile("deploy.yml", yaml)}
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: 6,
                padding: "5px 12px",
                borderRadius: M.rSm,
                border: `1px solid ${M.line2}`,
                background: M.surface2,
                color: M.fg2,
                fontFamily: M.fontSans,
                fontSize: 12,
                fontWeight: 500,
                cursor: yaml ? "pointer" : "not-allowed",
                opacity: yaml ? 1 : 0.5,
              }}
            >
              <Icon name="download" size={12} />
              Download .yml
            </button>
          </div>
        </div>

        {/* Code block */}
        <div
          style={{
            borderRadius: M.rLg,
            overflow: "hidden",
            border: `1px solid ${M.line}`,
          }}
        >
          {isLoading && (
            <div
              style={{
                padding: "32px 20px",
                background: M.bg,
                color: M.fg3,
                fontFamily: M.fontMono,
                fontSize: 13,
                textAlign: "center",
              }}
            >
              Loading template…
            </div>
          )}
          {error && (
            <div
              style={{
                padding: "32px 20px",
                background: M.bg,
                color: M.err,
                fontFamily: M.fontSans,
                fontSize: 13,
                textAlign: "center",
              }}
            >
              Failed to load template. Backend may not be running.
            </div>
          )}
          {yaml && (
            <SyntaxHighlighter
              language="yaml"
              style={atomOneDark}
              customStyle={{
                margin: 0,
                padding: "20px",
                background: M.bg,
                fontSize: 12.5,
                fontFamily: M.fontMono,
                lineHeight: 1.65,
                maxHeight: 480,
                overflowY: "auto",
              }}
              showLineNumbers
              lineNumberStyle={{ color: M.fg4, minWidth: 36, userSelect: "none" }}
            >
              {yaml}
            </SyntaxHighlighter>
          )}
        </div>
      </div>

      {/* Navigation */}
      <div style={{ display: "flex", justifyContent: "space-between" }}>
        <Button variant="ghost" size="md" leadingIcon="arrow-left" onClick={onBack}>
          Back
        </Button>
        <Button variant="primary" size="md" trailingIcon="arrow-right" onClick={onNext}>
          Next
        </Button>
      </div>
    </div>
  );
}
