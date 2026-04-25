import { describe, it, expect } from "vitest";
import { formatRelative } from "@/lib/time";

const NOW = new Date("2026-04-11T12:00:00Z").getTime();

describe("formatRelative", () => {
  it("returns 'just now' for diffs under 5 seconds", () => {
    expect(formatRelative(new Date(NOW - 2000).toISOString(), NOW)).toBe("just now");
  });
  it("formats seconds", () => {
    expect(formatRelative(new Date(NOW - 12_000).toISOString(), NOW)).toBe("12s ago");
  });
  it("formats minutes", () => {
    expect(formatRelative(new Date(NOW - 5 * 60_000).toISOString(), NOW)).toBe("5m ago");
  });
  it("formats hours", () => {
    expect(formatRelative(new Date(NOW - 3 * 3600_000).toISOString(), NOW)).toBe("3h ago");
  });
  it("formats days", () => {
    expect(formatRelative(new Date(NOW - 2 * 86_400_000).toISOString(), NOW)).toBe("2d ago");
  });
  it("handles future dates as 'just now'", () => {
    expect(formatRelative(new Date(NOW + 5000).toISOString(), NOW)).toBe("just now");
  });
});
