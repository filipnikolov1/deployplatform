import type { DeploymentStatus } from "@/types/deployment";
import { statusConfig, toAppStatus } from "@/lib/statusConfig";

export function StatusDot({ status }: { status: DeploymentStatus }) {
  const config = statusConfig[toAppStatus(status)];
  return (
    <span role="status" aria-label={config.label} className="inline-flex items-center">
      <span
        aria-hidden="true"
        className={`inline-block h-2 w-2 rounded-full dot-glow ${config.dotColor}`}
      />
      <span className="sr-only">{config.label}</span>
    </span>
  );
}
