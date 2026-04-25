import { forwardRef, useId, type InputHTMLAttributes, type ReactNode } from "react";

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  leading?: ReactNode;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, error, leading, id, className = "", ...rest },
  ref,
) {
  const fallbackId = useId();
  const inputId = id ?? fallbackId;
  return (
    <div className="flex w-full flex-col gap-2">
      <label
        htmlFor={inputId}
        className="pl-1 text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-300/72"
      >
        {label}
      </label>
      <div className="relative">
        {leading && (
          <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-slate-400">
            {leading}
          </span>
        )}
        <input
          ref={ref}
          id={inputId}
          aria-invalid={error ? "true" : undefined}
          aria-describedby={error ? `${inputId}-err` : undefined}
        className={`glass-control w-full px-4 py-3 text-[15px] text-text-primary outline-none focus:border-white/[0.16] focus:bg-black/60 ${leading ? "pl-11" : ""} ${error ? "border-status-failed/50" : ""} ${className}`}
          {...rest}
        />
      </div>
      {error && (
        <p
          id={`${inputId}-err`}
          role="alert"
          className="pl-1 text-sm text-status-failed"
        >
          {error}
        </p>
      )}
    </div>
  );
});
