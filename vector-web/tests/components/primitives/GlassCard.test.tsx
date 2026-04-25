import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { GlassCard } from "@/components/primitives/GlassCard";

describe("GlassCard", () => {
  it("renders children and applies the card radius by default", () => {
    render(<GlassCard data-testid="gc">hi</GlassCard>);
    const el = screen.getByTestId("gc");
    expect(el).toHaveTextContent("hi");
    expect(el.className).toMatch(/rounded-card/);
    expect(el.className).toMatch(/backdrop-blur-glass/);
  });

  it("applies the panel radius when variant=panel", () => {
    render(<GlassCard variant="panel" data-testid="gc">x</GlassCard>);
    expect(screen.getByTestId("gc").className).toMatch(/rounded-panel/);
  });

  it("forwards className", () => {
    render(<GlassCard className="p-10" data-testid="gc">x</GlassCard>);
    expect(screen.getByTestId("gc").className).toMatch(/p-10/);
  });
});
