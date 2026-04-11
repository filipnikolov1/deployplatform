import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import { Button } from "@/components/primitives/Button";

describe("Button", () => {
  it("renders children and handles clicks", async () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Launch</Button>);
    await userEvent.click(screen.getByRole("button", { name: "Launch" }));
    expect(onClick).toHaveBeenCalledOnce();
  });

  it("is disabled and shows a spinner when loading", () => {
    render(<Button loading>Restart</Button>);
    const btn = screen.getByRole("button", { name: "Restart" });
    expect(btn).toBeDisabled();
    expect(btn.querySelector("[data-testid='btn-spinner']")).toBeTruthy();
  });

  it("applies the ghost-red variant", () => {
    render(<Button variant="ghost-red">Stop</Button>);
    expect(screen.getByRole("button").className).toMatch(/border-status-failed/);
  });
});
