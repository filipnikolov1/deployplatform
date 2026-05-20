import { getSessionToken, verifySession } from "@/lib/auth";

export const dynamic = "force-dynamic";

async function isAuthorized(req: Request): Promise<boolean> {
  const token = getSessionToken(req);
  const secret = process.env.SESSION_SECRET;
  if (!token || !secret) return false;
  const payload = await verifySession(secret, token);
  return payload !== null;
}

export async function GET(req: Request, { params }: { params: { id: string } }) {
  if (!(await isAuthorized(req))) {
    return new Response("Unauthorized", { status: 401 });
  }
  const backend = process.env.BACKEND_URL;
  const apiKey = process.env.API_KEY;
  if (!backend || !apiKey) {
    return new Response("Server misconfigured", { status: 500 });
  }

  const { id } = params;

  let upstream: Response;
  try {
    upstream = await fetch(`${backend}/api/operations/${encodeURIComponent(id)}/progress`, {
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
