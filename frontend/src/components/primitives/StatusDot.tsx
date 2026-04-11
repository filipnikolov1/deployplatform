import type { DeploymentStatus } from "@/types/deployment";
import { statusTokens } from "@/lib/status";

export function StatusDot({ status }: { status: DeploymentStatus }) {
  const { color, label } = statusTokens(status);
  return (
    <span role="status" aria-label={label} className="inline-flex items-center">
      <span
        aria-hidden="true"
        className="inline-block h-2 w-2 rounded-full dot-glow"
        style={{ color, backgroundColor: color }}
      />
      <span className="sr-only">{label}</span>
    </span>
  );
}
