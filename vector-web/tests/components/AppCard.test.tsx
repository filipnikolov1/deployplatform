import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { AppsTable } from "@/components/dashboard/AppsTable";
import type { Deployment } from "@/types/deployment";

// Mock next/navigation for the router used in AppsTable
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  usePathname: () => "/",
  useSearchParams: () => new URLSearchParams(),
}));

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

describe("AppsTable (replaces AppCard)", () => {
  it("shows app name and image in the table", () => {
    render(<AppsTable apps={[app]} events={[]} />);
    expect(screen.getByText("demo-api")).toBeInTheDocument();
    expect(screen.getByText(/me\/demo-api:latest/)).toBeInTheDocument();
  });

  it("shows port in table row", () => {
    render(<AppsTable apps={[app]} events={[]} />);
    expect(screen.getByText(/:3000/)).toBeInTheDocument();
  });

  it("renders an empty message when no apps", () => {
    render(<AppsTable apps={[]} events={[]} />);
    expect(screen.getByText(/No apps match/i)).toBeInTheDocument();
  });
});
