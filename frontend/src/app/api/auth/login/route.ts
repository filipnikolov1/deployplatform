import { NextResponse } from "next/server";
import { buildSessionCookie, signSession, SESSION_TTL_SECONDS } from "@/lib/auth";
import { z } from "zod";

const bodySchema = z.object({ password: z.string().min(1) });

function constantTimeEquals(a: string, b: string): boolean {
  const ae = new TextEncoder().encode(a);
  const be = new TextEncoder().encode(b);
  if (ae.length !== be.length) return false;
  let diff = 0;
  for (let i = 0; i < ae.length; i++) diff |= ae[i] ^ be[i];
  return diff === 0;
}

export async function POST(req: Request) {
  const secret = process.env.SESSION_SECRET;
  const expected = process.env.DASHBOARD_PASSWORD;
  if (!secret || !expected) {
    return NextResponse.json({ error: "Server misconfigured" }, { status: 500 });
  }
  let parsed;
  try {
    parsed = bodySchema.safeParse(await req.json());
  } catch {
    return NextResponse.json({ error: "Invalid body" }, { status: 400 });
  }
  if (!parsed.success) {
    return NextResponse.json({ error: "Invalid body" }, { status: 400 });
  }
  if (!constantTimeEquals(parsed.data.password, expected)) {
    return NextResponse.json({ error: "Invalid password" }, { status: 401 });
  }
  const now = Math.floor(Date.now() / 1000);
  const token = await signSession(secret, { iat: now, exp: now + SESSION_TTL_SECONDS });
  const res = NextResponse.json({ ok: true });
  res.headers.set("set-cookie", buildSessionCookie(token, SESSION_TTL_SECONDS));
  return res;
}
