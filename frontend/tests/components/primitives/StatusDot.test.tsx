import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { StatusDot } from "@/components/primitives/StatusDot";

describe("StatusDot", () => {
  it("renders the dot and a visually-hidden status label", () => {
    render(<StatusDot status="running" />);
    expect(screen.getByText("Running")).toHaveClass("sr-only");
  });

  it("exposes the correct role=status", () => {
    render(<StatusDot status="failed" />);
    expect(screen.getByRole("status")).toHaveAccessibleName("Failed");
  });
});
