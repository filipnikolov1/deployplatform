import { CheckCircle2, XCircle, Info, Loader2, X } from "lucide-react";
import { GlassCard } from "./GlassCard";

export type ToastVariant = "success" | "error" | "info" | "progress";

const ICONS: Record<ToastVariant, React.ReactNode> = {
  success: <CheckCircle2 className="h-5 w-5 text-status-running" aria-hidden="true" />,
  error: <XCircle className="h-5 w-5 text-status-failed" aria-hidden="true" />,
  info: <Info className="h-5 w-5 text-accent-ghostLight" aria-hidden="true" />,
  progress: <Loader2 className="h-5 w-5 animate-spin text-accent-ghostLight" aria-hidden="true" />,
};

export function Toast({
  variant,
  message,
  sublabel,
  onDismiss,
  action,
}: {
  variant: ToastVariant;
  message: string;
  sublabel?: string;
  onDismiss: () => void;
  action?: { label: string; onClick: () => void };
}) {
  return (
    <GlassCard className="relative flex items-start gap-3 p-3 pr-2 min-w-[260px] max-w-sm overflow-hidden">
      <span className="mt-0.5 shrink-0">{ICONS[variant]}</span>
      <div className="flex-1 min-w-0">
        <p className="text-sm text-text-primary truncate">{message}</p>
        {sublabel && (
          <p className="mt-0.5 text-xs text-text-tertiary truncate">{sublabel}</p>
        )}
      </div>
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
      {variant === "progress" && <span className="lp-progress-bar" aria-hidden="true" />}
    </GlassCard>
  );
}
