import { describe, it, expect, beforeEach, vi } from "vitest";
import { GET } from "@/app/api/apps/route";
import { signSession } from "@/lib/auth";

describe("GET /api/apps", () => {
  beforeEach(() => {
    process.env.BACKEND_URL = "http://backend.test";
    process.env.API_KEY = "test-key";
    process.env.SESSION_SECRET = "test-secret";
    vi.restoreAllMocks();
  });

  it("returns 401 when unauthenticated", async () => {
    const res = await GET(new Request("http://localhost/api/apps"));
    expect(res.status).toBe(401);
  });

  it("forwards backend response when authenticated", async () => {
    const token = await signSession("test-secret", {
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 3600,
    });
    const spy = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify([{ appName: "demo" }]), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );
    const req = new Request("http://localhost/api/apps", {
      headers: { cookie: `vector-session=${token}` },
    });
    const res = await GET(req);
    expect(res.status).toBe(200);
    expect(spy).toHaveBeenCalledWith(
      "http://backend.test/api/apps",
      expect.objectContaining({
        headers: expect.objectContaining({ "X-API-Key": "test-key" }),
      }),
    );
    expect(await res.json()).toEqual([{ appName: "demo" }]);
  });
});
