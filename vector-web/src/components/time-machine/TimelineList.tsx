"use client";

import { FixedSizeList, type ListChildComponentProps } from "react-window";
import type { TimelineEvent } from "@/types/analyzer";

interface TimelineItemProps {
  event: TimelineEvent;
  onSelect: (sha: string) => void;
  onOpenCrash: (id: number) => void;
  isSelected: boolean;
}

interface Props {
  events: TimelineEvent[];
  renderItem: (props: TimelineItemProps) => React.ReactNode;
  onSelect: (sha: string) => void;
  onOpenCrash: (id: number) => void;
  selectedSha: string | null;
}

const ITEM_HEIGHT = 53;
const VIRTUALIZE_AFTER = 500;

export function TimelineList({ events, renderItem, onSelect, onOpenCrash, selectedSha }: Props) {
  if (events.length <= VIRTUALIZE_AFTER) {
    return (
      <div className="px-4">
        {events.map((event) =>
          renderItem({
            event,
            onSelect,
            onOpenCrash,
            isSelected: !!event.commitSha && event.commitSha === selectedSha,
          }),
        )}
      </div>
    );
  }

  const Row = ({ index, style }: ListChildComponentProps) => {
    const event = events[index];
    return (
      <div style={style} className="px-4">
        {renderItem({
          event,
          onSelect,
          onOpenCrash,
          isSelected: !!event.commitSha && event.commitSha === selectedSha,
        })}
      </div>
    );
  };

  return (
    <div>
      <div
        className="px-4 py-2 text-[11px]"
        style={{ color: "var(--c-fg-3)", borderBottom: "1px solid var(--c-border-1)" }}
      >
        Virtualized rendering enabled for {events.length.toLocaleString()} events.
      </div>
      <FixedSizeList
        height={520}
        itemCount={events.length}
        itemSize={ITEM_HEIGHT}
        width="100%"
        overscanCount={8}
      >
        {Row}
      </FixedSizeList>
    </div>
  );
}
