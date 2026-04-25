import type { DeploymentEvent } from "@/types/vector";
import { ActivityRow } from "./ActivityRow";

interface Props {
  events: DeploymentEvent[];
}

export function ActivityTimeline({ events }: Props) {
  return (
    <div className="overflow-hidden rounded-card border border-white/[0.08] bg-black/35 backdrop-blur-xl shadow-[0_18px_40px_rgba(0,0,0,0.35),inset_0_1px_0_rgba(255,255,255,0.04)]">
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
