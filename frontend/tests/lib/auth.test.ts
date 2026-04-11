import { describe, it, expect } from "vitest";
import { signSession, verifySession } from "@/lib/auth";

const SECRET = "a".repeat(64);

describe("auth", () => {
  it("signs a session and verifies it round-trip", async () => {
    const token = await signSession(SECRET, { iat: 1_000_000, exp: 2_000_000_000 });
    const payload = await verifySession(SECRET, token);
    expect(payload).toEqual({ iat: 1_000_000, exp: 2_000_000_000 });
  });

  it("rejects a tampered payload", async () => {
    const token = await signSession(SECRET, { iat: 1, exp: 2 });
    const [, sig] = token.split(".");
    const tampered = `${Buffer.from('{"iat":1,"exp":9999}').toString("base64url")}.${sig}`;
    expect(await verifySession(SECRET, tampered)).toBeNull();
  });

  it("rejects expired tokens", async () => {
    const past = Math.floor(Date.now() / 1000) - 10;
    const token = await signSession(SECRET, { iat: past - 1000, exp: past });
    expect(await verifySession(SECRET, token)).toBeNull();
  });

  it("rejects a wrong secret", async () => {
    const token = await signSession(SECRET, { iat: 1, exp: 9_999_999_999 });
    expect(await verifySession("b".repeat(64), token)).toBeNull();
  });
});
