import { CheckCircle2, XCircle, Info, X } from "lucide-react";
import { GlassCard } from "./GlassCard";

export type ToastVariant = "success" | "error" | "info";

const ICONS = {
  success: <CheckCircle2 className="h-5 w-5 text-status-running" aria-hidden="true" />,
  error: <XCircle className="h-5 w-5 text-status-failed" aria-hidden="true" />,
  info: <Info className="h-5 w-5 text-accent-ghostLight" aria-hidden="true" />,
};

export function Toast({
  variant,
  message,
  onDismiss,
  action,
}: {
  variant: ToastVariant;
  message: string;
  onDismiss: () => void;
  action?: { label: string; onClick: () => void };
}) {
  return (
    <GlassCard className="flex items-start gap-3 p-3 pr-2 min-w-[260px] max-w-sm">
      {ICONS[variant]}
      <p className="flex-1 text-sm text-text-primary">{message}</p>
      {action && (
        <button
          type="button"
          onClick={action.onClick}
          className="text-xs font-medium text-accent-ghostLight hover:text-white underline underline-offset-2"
        >
          {action.label}
        </button>
      )}
      <button
        type="button"
        onClick={onDismiss}
        aria-label="Dismiss notification"
        className="text-text-tertiary hover:text-text-primary"
      >
        <X className="h-4 w-4" />
      </button>
    </GlassCard>
  );
}
