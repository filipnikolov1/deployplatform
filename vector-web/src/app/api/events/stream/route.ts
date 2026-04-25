import { verifySession, SESSION_COOKIE_NAME } from "@/lib/auth";

export const dynamic = "force-dynamic";

async function isAuthorized(req: Request): Promise<boolean> {
  const raw = req.headers.get("cookie") ?? "";
  let token: string | undefined;
  for (const part of raw.split(";")) {
    const [k, ...v] = part.trim().split("=");
    if (k === SESSION_COOKIE_NAME) token = v.join("=");
  }
  const secret = process.env.SESSION_SECRET;
  if (!token || !secret) return false;
  const payload = await verifySession(secret, token);
  return payload !== null;
}

export async function GET(req: Request) {
  if (!(await isAuthorized(req))) {
    return new Response("Unauthorized", { status: 401 });
  }
  const backend = process.env.BACKEND_URL;
  const apiKey = process.env.API_KEY;
  if (!backend || !apiKey) {
    return new Response("Server misconfigured", { status: 500 });
  }

  let upstream: Response;
  try {
    upstream = await fetch(`${backend}/api/events/stream`, {
      headers: { "X-API-Key": apiKey, Accept: "text/event-stream" },
      cache: "no-store",
      signal: req.signal,
    });
  } catch {
    return new Response("Upstream unavailable", { status: 503 });
  }

  if (!upstream.ok || !upstream.body) {
    return new Response(`Upstream ${upstream.status}`, { status: upstream.status });
  }

  return new Response(upstream.body, {
    status: 200,
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache, no-transform",
      Connection: "keep-alive",
      "X-Accel-Buffering": "no",
    },
  });
}
