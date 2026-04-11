import { describe, it, expect, beforeEach, vi } from "vitest";
import { GET } from "@/app/api/apps/[appName]/logs/runtime/route";
import { signSession } from "@/lib/auth";

describe("SSE build log proxy", () => {
  beforeEach(() => {
    process.env.BACKEND_URL = "http://backend.test";
    process.env.API_KEY = "k";
    process.env.SESSION_SECRET = "s";
    vi.restoreAllMocks();
  });

  it("401s without session", async () => {
    const res = await GET(new Request("http://l/api/apps/x/logs/runtime"), {
      params: { appName: "x" },
    });
    expect(res.status).toBe(401);
  });

  it("streams upstream body with text/event-stream content-type", async () => {
    const token = await signSession("s", {
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 3600,
    });
    const stream = new ReadableStream({
      start(c) {
        c.enqueue(new TextEncoder().encode("data: hello\n\n"));
        c.close();
      },
    });
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(stream, {
        status: 200,
        headers: { "content-type": "text/event-stream" },
      }),
    );
    const req = new Request("http://l/api/apps/x/logs/runtime", {
      headers: { cookie: `launchpad-session=${token}` },
    });
    const res = await GET(req, { params: { appName: "x" } });
    expect(res.status).toBe(200);
    expect(res.headers.get("content-type")).toBe("text/event-stream");
    expect(await res.text()).toContain("data: hello");
  });
});
