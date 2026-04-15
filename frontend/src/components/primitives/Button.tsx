import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from "react";
import { Loader2 } from "lucide-react";

type Variant = "primary" | "ghost-purple" | "ghost-red" | "icon";

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  loading?: boolean;
  leadingIcon?: ReactNode;
}

const VARIANT: Record<Variant, string> = {
  primary:
    "rounded-full border border-white/[0.10] bg-black/45 px-5 py-2.5 text-sm font-medium text-white shadow-[0_14px_30px_rgba(0,0,0,0.35),inset_0_1px_0_rgba(255,255,255,0.08)] hover:bg-black/55",
  "ghost-purple":
    "rounded-full border border-white/[0.10] bg-black/35 px-4 py-2 text-sm text-slate-200 shadow-[inset_0_1px_0_rgba(255,255,255,0.06)] hover:bg-black/50",
  "ghost-red":
    "rounded-full border border-red-400/25 bg-red-500/10 px-4 py-2 text-sm text-red-200 shadow-[inset_0_1px_0_rgba(255,255,255,0.05)] hover:bg-red-500/16",
  icon:
    "flex h-11 w-11 items-center justify-center rounded-full border border-white/[0.08] bg-black/35 text-text-secondary shadow-[inset_0_1px_0_rgba(255,255,255,0.06)] hover:bg-black/50",
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = "primary", loading = false, leadingIcon, disabled, className = "", children, ...rest },
  ref,
) {
  const isDisabled = disabled || loading;
  return (
    <button
      ref={ref}
      disabled={isDisabled}
      aria-busy={loading || undefined}
      className={`${VARIANT[variant]} inline-flex items-center gap-2 backdrop-blur-xl transition-[filter,background-color,border-color,transform] duration-ui ${isDisabled ? "cursor-not-allowed opacity-60" : "active:translate-y-px"} ${className}`}
      {...rest}
    >
      {loading ? (
        <Loader2 data-testid="btn-spinner" className="h-4 w-4 animate-spin" aria-hidden="true" />
      ) : (
        leadingIcon
      )}
      {children}
    </button>
  );
});
