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
    <div className="flex flex-col gap-1.5 w-full">
      <label htmlFor={inputId} className="text-sm text-text-secondary">
        {label}
      </label>
      <div className="relative">
        {leading && (
          <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-text-tertiary">
            {leading}
          </span>
        )}
        <input
          ref={ref}
          id={inputId}
          aria-invalid={error ? "true" : undefined}
          aria-describedby={error ? `${inputId}-err` : undefined}
          className={`w-full rounded-full bg-surface-glass border border-border-glass px-4 py-2.5 text-base text-text-primary placeholder:text-text-tertiary backdrop-blur-glass ${leading ? "pl-10" : ""} ${className}`}
          {...rest}
        />
      </div>
      {error && (
        <p id={`${inputId}-err`} role="alert" className="text-sm text-status-failed">
          {error}
        </p>
      )}
    </div>
  );
});
