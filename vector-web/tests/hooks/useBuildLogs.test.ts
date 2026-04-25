import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useBuildLogs } from "@/hooks/useBuildLogs";

class FakeEventSource {
  static instances: FakeEventSource[] = [];
  onmessage: ((e: MessageEvent) => void) | null = null;
  onerror: ((e: Event) => void) | null = null;
  onopen: ((e: Event) => void) | null = null;
  readyState = 0;
  url: string;
  constructor(url: string) {
    this.url = url;
    FakeEventSource.instances.push(this);
  }
  close = vi.fn(() => {
    this.readyState = 2;
  });
  _open() {
    this.readyState = 1;
    this.onopen?.(new Event("open"));
  }
  _msg(data: string) {
    this.onmessage?.(new MessageEvent("message", { data }));
  }
}

describe("useBuildLogs", () => {
  beforeEach(() => {
    FakeEventSource.instances = [];
    (globalThis as unknown as { EventSource: typeof FakeEventSource }).EventSource =
      FakeEventSource;
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("opens an EventSource pointed at the proxy route", () => {
    renderHook(() => useBuildLogs("demo"));
    expect(FakeEventSource.instances).toHaveLength(1);
    expect(FakeEventSource.instances[0].url).toBe("/api/apps/demo/logs/runtime");
  });

  it("appends received lines", () => {
    const { result } = renderHook(() => useBuildLogs("demo"));
    act(() => {
      FakeEventSource.instances[0]._open();
      FakeEventSource.instances[0]._msg("pulling image...");
      FakeEventSource.instances[0]._msg("starting container");
    });
    expect(result.current.lines).toEqual([
      "pulling image...",
      "starting container",
    ]);
    expect(result.current.status).toBe("open");
  });

  it("ring-buffers at 5000 lines", () => {
    const { result } = renderHook(() => useBuildLogs("demo"));
    act(() => {
      FakeEventSource.instances[0]._open();
      for (let i = 0; i < 5050; i++) FakeEventSource.instances[0]._msg(`line ${i}`);
    });
    expect(result.current.lines).toHaveLength(5000);
    expect(result.current.lines[0]).toBe("line 50");
    expect(result.current.lines.at(-1)).toBe("line 5049");
  });

  it("closes the EventSource on unmount", () => {
    const { unmount } = renderHook(() => useBuildLogs("demo"));
    const es = FakeEventSource.instances[0];
    unmount();
    expect(es.close).toHaveBeenCalled();
  });
});
