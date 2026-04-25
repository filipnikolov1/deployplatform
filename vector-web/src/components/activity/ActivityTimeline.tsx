import type { DeploymentEvent } from "@/types/vector";
import { ActivityRow } from "./ActivityRow";

interface Props {
  events: DeploymentEvent[];
}

export function ActivityTimeline({ events }: Props) {
  return (
    <div className="overflow-hidden rounded-xl border border-white/[0.08] bg-black/35 backdrop-blur-xl shadow-card">
      <div className="divide-y divide-white/5">
        {events.map((event, index) => (
          <ActivityRow
            key={event.id}
            event={event}
            isLast={index === events.length - 1}
          />
        ))}
      </div>
    </div>
  );
}
