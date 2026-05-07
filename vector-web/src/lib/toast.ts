"use client";

/**
 * toast — Sonner wrapper with Vector-typed helpers.
 * Replaces the old useToast() context pattern.
 *
 * Usage:
 *   import { toast } from "@/lib/toast";
 *   toast.success("Deployed");
 *   toast.error("App is busy — try again in a moment");
 *   toast.action("Deleted app", { label: "Undo", onClick: () => restore() });
 *   toast.deploy(operationId, "my-app");
 */

import { toast as sonner } from "sonner";

export interface ToastActionOptions {
  label: string;
  onClick: () => void;
}

export interface ToastOptions {
  duration?: number;
  description?: string;
}

export interface DeployToastOptions extends ToastOptions {
  initialMessage?: string;
}

function isOnSuppressedPage(): boolean {
  if (typeof window === "undefined") return false;
  const path = window.location.pathname;
  return path === "/" || path === "/activity";
}

export const toast = {
  success(message: string, opts?: ToastOptions): void {
    sonner.success(message, {
      duration: opts?.duration ?? 4000,
      description: opts?.description,
    });
  },

  error(message: string, opts?: ToastOptions): void {
    sonner.error(message, {
      duration: opts?.duration ?? 5000,
      description: opts?.description,
    });
  },

  info(message: string, opts?: ToastOptions): void {
    sonner.info(message, {
      duration: opts?.duration ?? 4000,
      description: opts?.description,
    });
  },

  action(message: string, opts: ToastActionOptions & ToastOptions): void {
    sonner(message, {
      duration: opts.duration ?? 8000,
      description: opts.description,
      action: {
        label: opts.label,
        onClick: opts.onClick,
      },
    });
  },

  /**
   * Deploy ambient widget toast.
   * Renders an <OperationProgress> bar inside the toast via sonner.toast.custom().
   * Suppressed when the user is already on "/" or "/activity".
   *
   * NOTE: This function uses React.createElement via a dynamic import to avoid
   * circular imports at module load time. The promise resolves synchronously in
   * the browser (modules are already loaded) so the toast appears immediately.
   */
  deploy(
    operationId: string,
    appName: string,
    opts?: DeployToastOptions,
  ): void {
    if (isOnSuppressedPage()) return;

    const initialMessage = opts?.initialMessage ?? `Deploying ${appName}…`;

    // Use a deferred render — sonner.custom accepts a render function
    // that fires inside the Toaster, so OperationProgress renders in React context.
    import("@/design/OperationProgress").then(({ OperationProgress }) => {
      import("react").then((React) => {
        sonner.custom(
          () =>
            React.createElement(
              "div",
              {
                style: {
                  width: "100%",
                  display: "flex",
                  flexDirection: "column" as const,
                  gap: 8,
                },
              },
              React.createElement(
                "span",
                {
                  style: { fontSize: 13, fontWeight: 500 },
                },
                initialMessage,
              ),
              React.createElement(OperationProgress, {
                operationId,
                variant: "toast" as const,
              }),
            ),
          {
            duration: Infinity,
            id: `deploy-${operationId}`,
          },
        );
      });
    });
  },

  /** Dismiss a toast by id (used to close the deploy widget when finished) */
  dismiss(id: string): void {
    sonner.dismiss(id);
  },
} as const;
