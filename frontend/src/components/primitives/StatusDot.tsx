export type AppStatus = "running" | "failed" | "stopped" | "pending";

const LABELS: Record<AppStatus, string> = {
  running: "Running",
  failed: "Failed",
  stopped: "Stopped",
  pending: "Pending",
};

const COLORS: Record<AppStatus, string> = {
  running: "#22C55E",
  failed: "#EF4444",
  stopped: "#F59E0B",
  pending: "#64748B",
};

export function StatusDot({ status }: { status: AppStatus }) {
  const label = LABELS[status];
  return (
    <span
      role="status"
      aria-label={label}
      className="inline-flex items-center"
    >
      <span
        aria-hidden="true"
        className="inline-block h-2 w-2 rounded-full dot-glow"
        style={{ color: COLORS[status], backgroundColor: COLORS[status] }}
      />
      <span className="sr-only">{label}</span>
    </span>
  );
}
