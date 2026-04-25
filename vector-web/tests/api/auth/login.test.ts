import { describe, it, expect, beforeEach, vi } from "vitest";

beforeEach(() => {
  process.env.DASHBOARD_PASSWORD = "letmein";
  process.env.SESSION_SECRET = "x".repeat(64);
  vi.resetModules();
});

async function postJSON(body: unknown) {
  const { POST } = await import("@/app/api/auth/login/route");
  return POST(new Request("http://t/login", {
    method: "POST",
    body: JSON.stringify(body),
    headers: { "content-type": "application/json" },
  }));
}

describe("POST /api/auth/login", () => {
  it("401s on wrong password", async () => {
    const res = await postJSON({ password: "nope" });
    expect(res.status).toBe(401);
  });

  it("200s and sets cookie on correct password", async () => {
    const res = await postJSON({ password: "letmein" });
    expect(res.status).toBe(200);
    const setCookie = res.headers.get("set-cookie") ?? "";
    expect(setCookie).toMatch(/vector-session=/);
    expect(setCookie).toMatch(/HttpOnly/);
  });

  it("400s on missing password", async () => {
    const res = await postJSON({});
    expect(res.status).toBe(400);
  });
});
