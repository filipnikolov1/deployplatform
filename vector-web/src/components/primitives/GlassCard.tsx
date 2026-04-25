import {
  forwardRef,
  type ButtonHTMLAttributes,
  type HTMLAttributes,
  type Ref,
} from "react";

type Radius = "card" | "panel";

interface GlassCardBaseProps {
  variant?: Radius;
  radius?: Radius;
  className?: string;
}

export interface GlassCardDivProps
  extends GlassCardBaseProps,
    HTMLAttributes<HTMLDivElement> {
  as?: "div";
}

export interface GlassCardButtonProps
  extends GlassCardBaseProps,
    ButtonHTMLAttributes<HTMLButtonElement> {
  as: "button";
}

export type GlassCardProps = GlassCardDivProps | GlassCardButtonProps;

function resolveRadius(props: GlassCardBaseProps): Radius {
  return props.radius ?? props.variant ?? "card";
}

export const GlassCard = forwardRef(function GlassCard(
  props: GlassCardProps,
  ref: Ref<HTMLDivElement | HTMLButtonElement>,
) {
  const { className = "" } = props;
  const r = resolveRadius(props);
  const radiusClass = r === "panel" ? "rounded-2xl" : "rounded-xl";
  const base = `${radiusClass} border border-border-glass bg-surface-glass backdrop-blur-glass shadow-glass ${className}`;

  if (props.as === "button") {
    const { as: _as, variant: _variant, radius: _radius, className: _cn, children, ...rest } =
      props;
    return (
      <button
        ref={ref as Ref<HTMLButtonElement>}
        className={base}
        {...rest}
      >
        {children}
      </button>
    );
  }

  const {
    as: _as,
    variant: _variant,
    radius: _radius,
    className: _cn,
    children,
    ...rest
  } = props as GlassCardDivProps;
  return (
    <div ref={ref as Ref<HTMLDivElement>} className={base} {...rest}>
      {children}
    </div>
  );
});
