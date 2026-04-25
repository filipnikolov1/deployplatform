import { Skeleton } from "@/components/primitives/Skeleton";

export function AppDetailSkeleton() {
  return (
    <div aria-busy="true">
      <div className="flex items-center gap-3 border-b border-white/10 p-4">
        <Skeleton variant="circle" className="h-2 w-2" />
        <Skeleton variant="line" className="h-5 w-48 flex-1" />
        <Skeleton variant="block" className="h-9 w-24 rounded-full" />
        <Skeleton variant="block" className="h-9 w-20 rounded-full" />
        <Skeleton variant="circle" className="h-9 w-9" />
      </div>
      <div className="grid grid-cols-2 gap-x-6 gap-y-2 p-4 sm:grid-cols-4">
        {Array.from({ length: 5 }).map((_, i) => (
          <div key={i} className="flex flex-col gap-1">
            <Skeleton variant="line" className="h-3 w-12" />
            <Skeleton variant="line" className="h-4 w-24" />
          </div>
        ))}
      </div>
    </div>
  );
}
