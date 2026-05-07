"use client";

/**
 * LogsTab — F5.3
 * Virtualized log viewer with follow-tail, search, stream filter, AI analysis.
 */

import {
  useEffect,
  useRef,
  useState,
  useCallback,
} from "react";
import { FixedSizeList, type ListChildComponentProps } from "react-window";
import { motion, AnimatePresence } from "framer-motion";
import * as Dialog from "@radix-ui/react-dialog";
import { Sparkles, Download, X, Search, ArrowUp, ArrowDown } from "lucide-react";
import { M } from "@/design/tokens";
import { Button } from "@/design/primitives/Button";
import { useBuildLogs } from "@/hooks/useBuildLogs";
import { AiAnalysisPanel } from "../AiAnalysisPanel";
import { toast } from "@/lib/toast";

const ROW_HEIGHT = 22;

interface ParsedLine {
  raw: string;
  lineNum: number;
  stream: "STDOUT" | "STDERR" | "ALL";
  content: string;
  isRecent: boolean;
}

function parseStream(raw: string): "STDOUT" | "STDERR" {
  if (raw.startsWith("[STDERR]") || raw.toLowerCase().includes("stderr")) return "STDERR";
  return "STDOUT";
}

interface LogRowData {
  lines: ParsedLine[];
  searchMatches: Set<number>;
  activeMatch: number | null;
}

function LogRowRenderer({ index, style, data }: ListChildComponentProps<LogRowData>) {
  const { lines, searchMatches, activeMatch } = data;
  const line = lines[index];
  if (!line) return null;

  const isMatch = searchMatches.has(index);
  const isActiveMatch = activeMatch === index;

  const bg: string = isActiveMatch
    ? "rgba(167,139,250,0.25)"
    : isMatch
      ? "rgba(167,139,250,0.10)"
      : line.isRecent
        ? "rgba(167,139,250,0.06)"
        : "transparent";

  return (
    <div
      style={{
        ...style,
        display: "flex",
        alignItems: "center",
        gap: 0,
        background: bg,
        transition: "background 3s ease",
        fontFamily: M.fontMono,
        fontSize: 11.5,
        lineHeight: "22px",
        paddingLeft: 4,
        paddingRight: 8,
        whiteSpace: "nowrap",
      }}
    >
      {/* Line number gutter */}
      <span
        style={{
          width: 40,
          flexShrink: 0,
          textAlign: "right",
          paddingRight: 10,
          color: M.fg4,
          userSelect: "none",
          fontSize: 10,
        }}
      >
        {line.lineNum}
      </span>

      {/* Stream tag */}
      <span
        style={{
          width: 50,
          flexShrink: 0,
          fontSize: 9,
          fontWeight: 700,
          letterSpacing: "0.06em",
          paddingRight: 8,
          color: line.stream === "STDERR" ? M.err : M.fg3,
        }}
      >
        {line.stream}
      </span>

      {/* Content */}
      <span
        style={{
          flex: 1,
          overflow: "hidden",
          textOverflow: "ellipsis",
          color: line.stream === "STDERR" ? "rgba(252,165,165,0.85)" : M.fg2,
        }}
      >
        {line.content}
      </span>
    </div>
  );
}

type StreamFilter = "ALL" | "STDOUT" | "STDERR";

interface FilterPillProps {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}

function FilterPill({ active, onClick, children }: FilterPillProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      style={{
        padding: "3px 10px",
        borderRadius: M.rPill,
        fontSize: 11,
        fontFamily: M.fontMono,
        fontWeight: 600,
        letterSpacing: "0.06em",
        textTransform: "uppercase",
        cursor: "pointer",
        border: `1px solid ${active ? M.accentLine : M.line}`,
        background: active ? M.accentSoft : "transparent",
        color: active ? M.accentLight : M.fg3,
        outline: "none",
        transition: "all 150ms",
      }}
    >
      {children}
    </button>
  );
}

interface LogsTabProps {
  appName: string;
}

export function LogsTab({ appName }: LogsTabProps) {
  const { lines: rawLines, status } = useBuildLogs(appName);
  const [streamFilter, setStreamFilter] = useState<StreamFilter>("ALL");
  const [followTail, setFollowTail] = useState(true);
  const [userScrolledUp, setUserScrolledUp] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [activeMatchIdx, setActiveMatchIdx] = useState(0);
  const [analysisOpen, setAnalysisOpen] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [recentChunkEnd, setRecentChunkEnd] = useState<number>(-1);

  const listRef = useRef<FixedSizeList>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const prevLenRef = useRef(0);
  const recentTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const liveIndicatorTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [showLiveDot, setShowLiveDot] = useState(false);
  const [containerHeight, setContainerHeight] = useState(400);

  // Track container height via ResizeObserver
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const ro = new ResizeObserver(() => {
      setContainerHeight(el.clientHeight);
    });
    ro.observe(el);
    setContainerHeight(el.clientHeight);
    return () => ro.disconnect();
  }, []);

  // Build parsed lines
  const parsedLines: ParsedLine[] = rawLines.map((raw, i) => ({
    raw,
    lineNum: i + 1,
    stream: parseStream(raw),
    content: raw.replace(/^\[STD(OUT|ERR)\]\s?/i, ""),
    isRecent: i <= recentChunkEnd && recentChunkEnd >= 0,
  }));

  // Apply stream filter
  const filteredLines = streamFilter === "ALL"
    ? parsedLines
    : parsedLines.filter((l) => l.stream === streamFilter);

  // Track new lines arriving
  useEffect(() => {
    if (rawLines.length > prevLenRef.current) {
      // Mark new chunk as recent
      setRecentChunkEnd(rawLines.length - 1);
      if (recentTimerRef.current) clearTimeout(recentTimerRef.current);
      recentTimerRef.current = setTimeout(() => setRecentChunkEnd(-1), 3000);

      // Show live dot
      setShowLiveDot(true);
      if (liveIndicatorTimerRef.current) clearTimeout(liveIndicatorTimerRef.current);
      liveIndicatorTimerRef.current = setTimeout(() => setShowLiveDot(false), 5000);

      // Follow tail
      if (followTail && !userScrolledUp && listRef.current) {
        listRef.current.scrollToItem(filteredLines.length - 1, "end");
      }
    }
    prevLenRef.current = rawLines.length;
  }, [rawLines.length, filteredLines.length, followTail, userScrolledUp]);

  // Search logic
  const matchIndices: number[] = [];
  if (searchQuery.trim()) {
    const q = searchQuery.toLowerCase();
    filteredLines.forEach((l, i) => {
      if (l.content.toLowerCase().includes(q)) matchIndices.push(i);
    });
  }
  const matchSet = new Set(matchIndices);
  const activeMatchLineIdx = matchIndices.length > 0 ? matchIndices[activeMatchIdx % matchIndices.length] : null;

  // Jump to match
  useEffect(() => {
    if (activeMatchLineIdx !== null && listRef.current) {
      listRef.current.scrollToItem(activeMatchLineIdx, "center");
    }
  }, [activeMatchLineIdx]);

  // Keyboard shortcut Cmd+F
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "f") {
        e.preventDefault();
        setSearchOpen(true);
      }
      if (e.key === "Escape" && searchOpen) {
        setSearchOpen(false);
        setSearchQuery("");
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [searchOpen]);

  const handleScroll = useCallback(({ scrollOffset }: { scrollOffset: number }) => {
    if (!containerRef.current) return;
    const visibleHeight = containerRef.current.clientHeight;
    const totalHeight = filteredLines.length * ROW_HEIGHT;
    const distFromBottom = totalHeight - scrollOffset - visibleHeight;
    if (distFromBottom > 40) {
      setUserScrolledUp(true);
      setFollowTail(false);
    } else {
      setUserScrolledUp(false);
    }
  }, [filteredLines.length]);

  const jumpToLive = () => {
    setUserScrolledUp(false);
    setFollowTail(true);
    if (listRef.current && filteredLines.length > 0) {
      listRef.current.scrollToItem(filteredLines.length - 1, "end");
    }
  };

  const handleDownload = async () => {
    if (downloading) return;
    setDownloading(true);
    try {
      const res = await fetch(`/api/apps/${encodeURIComponent(appName)}/logs/runtime/download`);
      if (!res.ok) { toast.error("Log download failed"); return; }
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `${appName}-runtime.log`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch {
      toast.error("Log download failed");
    } finally {
      setDownloading(false);
    }
  };

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        height: "100%",
        minHeight: 0,
        gap: 10,
        position: "relative",
      }}
    >
      {/* ── Top strip ── */}
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 8,
          flexShrink: 0,
          flexWrap: "wrap",
        }}
      >
        {/* Live dot + line count */}
        <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
          {showLiveDot && (
            <motion.span
              animate={{ opacity: [1, 0.3, 1] }}
              transition={{ duration: 1.2, repeat: Infinity }}
              style={{
                width: 6,
                height: 6,
                borderRadius: "50%",
                background: M.accent,
                flexShrink: 0,
              }}
            />
          )}
          <span
            style={{
              fontFamily: M.fontMono,
              fontSize: 11,
              color: M.fg3,
            }}
          >
            {status === "connecting" && "connecting…"}
            {status === "open" && `${filteredLines.length} lines`}
            {status === "error" && "connection error"}
            {status === "closed" && `${filteredLines.length} lines · closed`}
          </span>
        </div>

        {/* Stream filter pills */}
        <div style={{ display: "flex", gap: 4 }}>
          {(["ALL", "STDOUT", "STDERR"] as StreamFilter[]).map((f) => (
            <FilterPill key={f} active={streamFilter === f} onClick={() => setStreamFilter(f)}>
              {f}
            </FilterPill>
          ))}
        </div>

        <div style={{ marginLeft: "auto", display: "flex", alignItems: "center", gap: 6 }}>
          {/* Follow tail switch */}
          <label
            style={{
              display: "flex",
              alignItems: "center",
              gap: 6,
              cursor: "pointer",
              fontSize: 11,
              color: M.fg3,
              fontFamily: M.fontSans,
              userSelect: "none",
            }}
          >
            <button
              type="button"
              role="switch"
              aria-checked={followTail}
              onClick={() => {
                setFollowTail((v) => {
                  if (!v) {
                    setUserScrolledUp(false);
                    setTimeout(() => {
                      if (listRef.current && filteredLines.length > 0) {
                        listRef.current.scrollToItem(filteredLines.length - 1, "end");
                      }
                    }, 0);
                  }
                  return !v;
                });
              }}
              style={{
                width: 28,
                height: 16,
                borderRadius: M.rPill,
                background: followTail ? M.accent : M.surface2,
                border: `1px solid ${followTail ? M.accentLine : M.line2}`,
                cursor: "pointer",
                position: "relative",
                outline: "none",
                flexShrink: 0,
                transition: "background 150ms",
              }}
            >
              <motion.span
                animate={{ x: followTail ? 13 : 1 }}
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
            Follow
          </label>

          {/* Search */}
          <Button
            variant="icon"
            size="sm"
            onClick={() => setSearchOpen(true)}
            aria-label="Search logs"
          >
            <Search style={{ width: 13, height: 13 }} />
          </Button>

          {/* Ask AI */}
          <Button
            variant="accent"
            size="sm"
            onClick={() => setAnalysisOpen(true)}
          >
            <Sparkles style={{ width: 12, height: 12 }} />
            Ask AI
          </Button>

          {/* Download */}
          <Button
            variant="icon"
            size="sm"
            onClick={handleDownload}
            disabled={downloading}
            aria-label="Download logs"
          >
            <Download style={{ width: 13, height: 13 }} />
          </Button>
        </div>
      </div>

      {/* ── Log body ── */}
      <div
        ref={containerRef}
        style={{
          flex: 1,
          minHeight: 0,
          background: M.bg,
          border: `1px solid ${M.line}`,
          borderRadius: M.rMd,
          overflow: "hidden",
          position: "relative",
        }}
      >
        {filteredLines.length === 0 ? (
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              height: "100%",
              fontFamily: M.fontMono,
              fontSize: 12,
              color: M.fg4,
            }}
          >
            Waiting for logs…
          </div>
        ) : (
          <FixedSizeList
            ref={listRef}
            height={containerHeight}
            width="100%"
            itemCount={filteredLines.length}
            itemSize={ROW_HEIGHT}
            itemData={{ lines: filteredLines, searchMatches: matchSet, activeMatch: activeMatchLineIdx }}
            onScroll={handleScroll}
            style={{ outline: "none" }}
          >
            {LogRowRenderer}
          </FixedSizeList>
        )}

        {/* Jump to live floating button */}
        <AnimatePresence>
          {userScrolledUp && (
            <motion.button
              type="button"
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: 8 }}
              transition={{ duration: 0.18 }}
              onClick={jumpToLive}
              style={{
                position: "absolute",
                bottom: 12,
                right: 12,
                display: "inline-flex",
                alignItems: "center",
                gap: 5,
                padding: "5px 12px",
                borderRadius: M.rPill,
                fontSize: 11,
                fontFamily: M.fontSans,
                fontWeight: 500,
                color: M.accentLight,
                background: M.accentSoft,
                border: `1px solid ${M.accentLine}`,
                cursor: "pointer",
                outline: "none",
              }}
            >
              <ArrowDown style={{ width: 11, height: 11 }} />
              Jump to live
            </motion.button>
          )}
        </AnimatePresence>
      </div>

      {/* ── Search overlay (Cmd+F) ── */}
      <Dialog.Root open={searchOpen} onOpenChange={(v) => { setSearchOpen(v); if (!v) { setSearchQuery(""); setActiveMatchIdx(0); } }}>
        <Dialog.Portal>
          <Dialog.Overlay
            style={{
              position: "fixed",
              inset: 0,
              background: "rgba(0,0,0,0.4)",
              backdropFilter: "blur(2px)",
              zIndex: 200,
            }}
          />
          <Dialog.Content
            style={{
              position: "fixed",
              top: "20%",
              left: "50%",
              transform: "translateX(-50%)",
              width: 480,
              maxWidth: "calc(100vw - 32px)",
              background: M.surface,
              border: `1px solid ${M.line2}`,
              borderRadius: M.rLg,
              padding: 20,
              boxShadow: "0 24px 60px rgba(0,0,0,0.6)",
              zIndex: 201,
              outline: "none",
            }}
          >
            <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14 }}>
              <Search style={{ width: 14, height: 14, color: M.fg3, flexShrink: 0 }} />
              <input
                autoFocus
                value={searchQuery}
                onChange={(e) => {
                  setSearchQuery(e.target.value);
                  setActiveMatchIdx(0);
                }}
                placeholder="Search logs…"
                style={{
                  flex: 1,
                  background: "transparent",
                  border: "none",
                  outline: "none",
                  fontFamily: M.fontMono,
                  fontSize: 13,
                  color: M.fg,
                }}
              />
              <span
                style={{
                  fontSize: 11,
                  fontFamily: M.fontMono,
                  color: M.fg3,
                  whiteSpace: "nowrap",
                  flexShrink: 0,
                }}
              >
                {matchIndices.length > 0
                  ? `${(activeMatchIdx % matchIndices.length) + 1} / ${matchIndices.length}`
                  : searchQuery
                    ? "0 matches"
                    : ""}
              </span>
              <Dialog.Close asChild>
                <button
                  type="button"
                  style={{
                    display: "inline-flex",
                    alignItems: "center",
                    justifyContent: "center",
                    padding: 4,
                    borderRadius: M.rSm,
                    background: "transparent",
                    border: "none",
                    color: M.fg3,
                    cursor: "pointer",
                    outline: "none",
                  }}
                >
                  <X style={{ width: 14, height: 14 }} />
                </button>
              </Dialog.Close>
            </div>

            <div style={{ display: "flex", gap: 8 }}>
              <Button
                variant="secondary"
                size="sm"
                onClick={() =>
                  setActiveMatchIdx((i) => (i - 1 + matchIndices.length) % Math.max(matchIndices.length, 1))
                }
                disabled={matchIndices.length === 0}
              >
                <ArrowUp style={{ width: 12, height: 12 }} />
                Prev
              </Button>
              <Button
                variant="secondary"
                size="sm"
                onClick={() =>
                  setActiveMatchIdx((i) => (i + 1) % Math.max(matchIndices.length, 1))
                }
                disabled={matchIndices.length === 0}
              >
                Next
                <ArrowDown style={{ width: 12, height: 12 }} />
              </Button>
            </div>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>

      {/* ── AI Analysis overlay ── */}
      {analysisOpen && (
        <div
          style={{
            position: "absolute",
            inset: 0,
            zIndex: 20,
            display: "flex",
            alignItems: "flex-start",
            justifyContent: "center",
          }}
        >
          <button
            type="button"
            aria-label="Close analysis"
            onClick={() => setAnalysisOpen(false)}
            style={{
              position: "absolute",
              inset: 0,
              background: "rgba(0,0,0,0.55)",
              backdropFilter: "blur(4px)",
              cursor: "default",
              border: "none",
            }}
          />
          <div
            style={{
              position: "relative",
              zIndex: 1,
              width: "100%",
              marginTop: 40,
              maxHeight: "calc(100% - 60px)",
              display: "flex",
              flexDirection: "column",
              overflow: "hidden",
            }}
          >
            <AiAnalysisPanel appName={appName} onClose={() => setAnalysisOpen(false)} />
          </div>
        </div>
      )}
    </div>
  );
}
