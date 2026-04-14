import type { DeploymentEvent } from "@/types/launchpad";
import { ActivityRow } from "./ActivityRow";

interface Props {
  events: DeploymentEvent[];
}

export function ActivityTimeline({ events }: Props) {
  return (
    <div className="rounded-xl border border-white/10 bg-white/5 backdrop-blur-xl overflow-hidden">
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
