"use client";

/**
 * Button — motion-aware button primitive.
 * Variants: primary | secondary | ghost | accent | icon
 * Sizes: sm | md
 * Forwards ref. Respects prefers-reduced-motion.
 */

import { forwardRef, CSSProperties } from "react";
import { motion } from "framer-motion";
import { M } from "@/design/tokens";
import { Icon } from "./Icon";
import { usePrefersReducedMotion } from "@/hooks/usePrefersReducedMotion";

export type ButtonVariant = "primary" | "secondary" | "ghost" | "accent" | "icon";
export type ButtonSize = "sm" | "md";

const SIZE_MAP = {
  sm: { padding: "6px 12px", fontSize: 13, gap: 6, iconSize: 13 },
  md: { padding: "8px 14px", fontSize: 13.5, gap: 7, iconSize: 14 },
} as const;

const VARIANT_MAP: Record<ButtonVariant, CSSProperties & { _padding?: string | number; _width?: number; _height?: number }> = {
  primary: {
    background: M.fg,
    color: M.bg,
    border: `1px solid ${M.fg}`,
  },
  secondary: {
    background: "transparent",
    color: M.fg,
    border: `1px solid ${M.line2}`,
  },
  ghost: {
    background: "transparent",
    color: M.fg2,
    border: "1px solid transparent",
  },
  accent: {
    background: M.accentSoft,
    color: M.accentLight,
    border: `1px solid ${M.accentLine}`,
  },
  icon: {
    background: "transparent",
    color: M.fg2,
    border: "1px solid transparent",
    _padding: 8,
    _width: 32,
    _height: 32,
  },
};

export interface ButtonProps
  extends Omit<
    React.ButtonHTMLAttributes<HTMLButtonElement>,
    | "children"
    | "onDrag"
    | "onDragStart"
    | "onDragEnd"
    | "onDragEnter"
    | "onDragLeave"
    | "onDragOver"
    | "onDragExit"
    | "onAnimationStart"
    | "onAnimationEnd"
    | "onAnimationIteration"
  > {
  variant?: ButtonVariant;
  size?: ButtonSize;
  leadingIcon?: string;
  trailingIcon?: string;
  children?: React.ReactNode;
  style?: CSSProperties;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    variant = "secondary",
    size = "md",
    leadingIcon,
    trailingIcon,
    children,
    style,
    disabled,
    ...rest
  },
  ref,
) {
  const reduced = usePrefersReducedMotion();
  const sizeTokens = SIZE_MAP[size];
  const variantTokens = VARIANT_MAP[variant];

  const isIcon = variant === "icon";
  const basePadding = isIcon
    ? (variantTokens._padding ?? sizeTokens.padding)
    : sizeTokens.padding;

  const computedStyle: CSSProperties = {
    display: "inline-flex",
    alignItems: "center",
    justifyContent: isIcon ? "center" : undefined,
    gap: sizeTokens.gap,
    padding: isIcon ? basePadding : sizeTokens.padding,
    width: isIcon ? (variantTokens._width ?? 32) : undefined,
    height: isIcon ? (variantTokens._height ?? 32) : undefined,
    fontSize: sizeTokens.fontSize,
    fontFamily: M.fontSans,
    fontWeight: 500,
    borderRadius: M.rPill,
    cursor: disabled ? "not-allowed" : "pointer",
    whiteSpace: "nowrap",
    letterSpacing: "-0.005em",
    opacity: disabled ? 0.5 : 1,
    background: variantTokens.background,
    color: variantTokens.color,
    border: variantTokens.border,
    outline: "none",
    textDecoration: "none",
    ...style,
  };

  return (
    <motion.button
      ref={ref}
      whileHover={reduced || disabled ? undefined : { y: -1, transition: { duration: 0.15 } }}
      whileTap={reduced || disabled ? undefined : { scale: 0.97, transition: { duration: 0.08 } }}
      style={computedStyle}
      disabled={disabled}
      {...rest}
    >
      {leadingIcon && (
        <Icon name={leadingIcon} size={sizeTokens.iconSize} />
      )}
      {children}
      {trailingIcon && (
        <Icon name={trailingIcon} size={sizeTokens.iconSize} />
      )}
    </motion.button>
  );
});
