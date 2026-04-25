import { describe, it, expect, beforeEach, vi } from "vitest";
import { proxyToBackend } from "@/lib/proxy";
import { signSession } from "@/lib/auth";

const SECRET = "test-session-secret-please-ignore";
const API_KEY = "test-api-key";
const BACKEND_URL = "http://backend.test";

function makeRequest(opts: { cookie?: string; method?: string; body?: string } = {}) {
  const headers = new Headers();
  if (opts.cookie) headers.set("cookie", opts.cookie);
  return new Request("http://localhost/api/apps", {
    method: opts.method ?? "GET",
    headers,
    body: opts.body,
  });
}

describe("proxyToBackend", () => {
  beforeEach(() => {
    process.env.BACKEND_URL = BACKEND_URL;
    process.env.API_KEY = API_KEY;
    process.env.SESSION_SECRET = SECRET;
    vi.restoreAllMocks();
  });

  it("returns 401 when no session cookie present", async () => {
    const res = await proxyToBackend(makeRequest(), "/api/apps");
    expect(res.status).toBe(401);
  });

  it("returns 401 when session signature is invalid", async () => {
    const res = await proxyToBackend(
      makeRequest({ cookie: "launchpad-session=not-a-real-token" }),
      "/api/apps",
    );
    expect(res.status).toBe(401);
  });

  it("forwards to backend with X-API-Key when session is valid", async () => {
    const token = await signSession(SECRET, {
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 3600,
    });
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify([{ appName: "a" }]), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );

    const res = await proxyToBackend(
      makeRequest({ cookie: `launchpad-session=${token}` }),
      "/api/apps",
    );

    expect(res.status).toBe(200);
    expect(fetchSpy).toHaveBeenCalledOnce();
    const [url, init] = fetchSpy.mock.calls[0];
    expect(url).toBe("http://backend.test/api/apps");
    expect((init as RequestInit).headers).toMatchObject({ "X-API-Key": API_KEY });
    const body = await res.json();
    expect(body).toEqual([{ appName: "a" }]);
  });

  it("passes through non-2xx backend responses unchanged", async () => {
    const token = await signSession(SECRET, {
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 3600,
    });
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response("not found", { status: 404, headers: { "content-type": "text/plain" } }),
    );

    const res = await proxyToBackend(
      makeRequest({ cookie: `launchpad-session=${token}` }),
      "/api/apps/nope",
    );

    expect(res.status).toBe(404);
    expect(await res.text()).toBe("not found");
  });
});
