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
    "bg-accent-primary text-white hover:brightness-110 rounded-full px-5 py-2.5 text-sm font-medium",
  "ghost-purple":
    "bg-transparent border border-accent-ghost text-accent-ghostLight hover:bg-accent-ghost/10 rounded-full px-4 py-2 text-sm",
  "ghost-red":
    "bg-transparent border border-status-failed text-[#F87171] hover:bg-status-failed/10 rounded-full px-4 py-2 text-sm",
  icon:
    "bg-transparent text-text-secondary hover:bg-white/5 rounded-full w-11 h-11 flex items-center justify-center",
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
      className={`${VARIANT[variant]} inline-flex items-center gap-2 transition-[filter,background-color] duration-ui ${isDisabled ? "opacity-60 cursor-not-allowed" : ""} ${className}`}
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
