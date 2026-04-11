import { forwardRef, type HTMLAttributes } from "react";

type Variant = "card" | "panel";

export interface GlassCardProps extends HTMLAttributes<HTMLDivElement> {
  variant?: Variant;
}

export const GlassCard = forwardRef<HTMLDivElement, GlassCardProps>(function GlassCard(
  { variant = "card", className = "", children, ...rest },
  ref,
) {
  const radius = variant === "panel" ? "rounded-panel" : "rounded-card";
  return (
    <div
      ref={ref}
      className={`${radius} border border-border-glass bg-surface-glass backdrop-blur-glass shadow-glass ${className}`}
      {...rest}
    >
      {children}
    </div>
  );
});
