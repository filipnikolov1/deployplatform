import { GlassCard } from "@/components/primitives/GlassCard";
import { Skeleton } from "@/components/primitives/Skeleton";

export function AppCardSkeleton() {
  return (
    <GlassCard radius="card" className="flex flex-col gap-3 p-4" aria-busy="true">
      <div className="flex items-center gap-2">
        <Skeleton variant="circle" className="h-2 w-2" />
        <Skeleton variant="line" className="h-4 w-32" />
      </div>
      <div className="flex flex-col gap-2">
        <Skeleton variant="line" className="h-3 w-48" />
        <Skeleton variant="line" className="h-3 w-20" />
        <Skeleton variant="line" className="h-3 w-24" />
      </div>
    </GlassCard>
  );
}
