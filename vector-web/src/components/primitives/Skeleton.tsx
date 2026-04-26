type Variant = "line" | "block" | "circle";

export interface SkeletonProps {
  variant?: Variant;
  width?: string | number;
  height?: string | number;
  className?: string;
}

export function Skeleton({ variant = "block", width, height, className = "" }: SkeletonProps) {
  const shape =
    variant === "circle"
      ? "rounded-full"
      : variant === "line"
        ? "rounded-full h-3"
        : "rounded-xl";
  const style: React.CSSProperties = {};
  if (width !== undefined) style.width = typeof width === "number" ? `${width}px` : width;
  if (height !== undefined) style.height = typeof height === "number" ? `${height}px` : height;
  return (
    <div
      aria-hidden="true"
      className={`bg-white/[0.04] border border-border-glass skeleton-shimmer ${shape} ${className}`}
      style={style}
    />
  );
}
