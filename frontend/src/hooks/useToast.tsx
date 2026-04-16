"use client";
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { Toast, type ToastVariant } from "@/components/primitives/Toast";

interface ToastAction {
  label: string;
  onClick: () => void;
}

interface ToastEntry {
  id: number;
  variant: ToastVariant;
  message: string;
  action?: ToastAction;
  durationMs: number;
}

interface PushOptions {
  action?: ToastAction;
  durationMs?: number;
}

interface ToastApi {
  success: (msg: string, opts?: PushOptions) => void;
  error: (msg: string, opts?: PushOptions) => void;
  info: (msg: string, opts?: PushOptions) => void;
}

const Ctx = createContext<ToastApi | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastEntry[]>([]);

  const push = useCallback(
    (variant: ToastVariant, message: string, opts?: PushOptions) => {
      const id = Date.now() + Math.random();
      const entry: ToastEntry = {
        id,
        variant,
        message,
        action: opts?.action,
        durationMs: opts?.durationMs ?? 4000,
      };
      setToasts((prev) => [...prev, entry].slice(-3));
    },
    [],
  );

  const dismiss = useCallback((id: number) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  useEffect(() => {
    if (toasts.length === 0) return;
    const timers = toasts.map((t) =>
      window.setTimeout(() => dismiss(t.id), t.durationMs),
    );
    return () => timers.forEach(window.clearTimeout);
  }, [toasts, dismiss]);

  const api: ToastApi = {
    success: (m, opts) => push("success", m, opts),
    error: (m, opts) => push("error", m, opts),
    info: (m, opts) => push("info", m, opts),
  };

  return (
    <Ctx.Provider value={api}>
      {children}
      <div
        aria-live="polite"
        className="pointer-events-none fixed top-4 right-4 z-[120] flex flex-col gap-2"
        style={{ paddingTop: "env(safe-area-inset-top)" }}
      >
        {toasts.map((t) => (
          <div key={t.id} className="pointer-events-auto">
            <Toast
              variant={t.variant}
              message={t.message}
              action={
                t.action
                  ? {
                      label: t.action.label,
                      onClick: () => {
                        t.action!.onClick();
                        dismiss(t.id);
                      },
                    }
                  : undefined
              }
              onDismiss={() => dismiss(t.id)}
            />
          </div>
        ))}
      </div>
    </Ctx.Provider>
  );
}

export function useToast(): ToastApi {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useToast must be used inside <ToastProvider>");
  return ctx;
}
