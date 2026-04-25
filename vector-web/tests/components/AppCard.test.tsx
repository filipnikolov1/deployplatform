import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { AppCard } from "@/components/dashboard/AppCard";
import type { Deployment } from "@/types/deployment";

const app: Deployment = {
  id: 1,
  appName: "demo-api",
  repoUrl: "https://github.com/me/demo-api",
  imageName: "me/demo-api:latest",
  containerPort: 3000,
  status: "RUNNING",
  createdAt: "2026-04-11T00:00:00Z",
  updatedAt: new Date(Date.now() - 60_000).toISOString(),
};

describe("AppCard", () => {
  it("shows name, image, port, and a status label", () => {
    render(<AppCard app={app} onOpen={() => {}} />);
    expect(screen.getByText("demo-api")).toBeInTheDocument();
    expect(screen.getByText(/me\/demo-api:latest/)).toBeInTheDocument();
    expect(screen.getByText(/3000/)).toBeInTheDocument();
    expect(screen.getByText("Running")).toBeInTheDocument();
  });

  it("calls onOpen with the appName when clicked", () => {
    const onOpen = vi.fn();
    render(<AppCard app={app} onOpen={onOpen} />);
    fireEvent.click(screen.getByRole("button", { name: /demo-api/i }));
    expect(onOpen).toHaveBeenCalledWith("demo-api");
  });

  it("is keyboard-activatable via Enter", () => {
    const onOpen = vi.fn();
    render(<AppCard app={app} onOpen={onOpen} />);
    const card = screen.getByRole("button", { name: /demo-api/i });
    card.focus();
    fireEvent.keyDown(card, { key: "Enter" });
    expect(onOpen).toHaveBeenCalledWith("demo-api");
  });
});
