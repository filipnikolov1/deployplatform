import { getSessionToken, verifySession } from "@/lib/auth";

export const dynamic = "force-dynamic";

async function isAuthorized(req: Request): Promise<boolean> {
  const token = getSessionToken(req);
  const secret = process.env.VECTOR_SESSION_SECRET;
  if (!token || !secret) return false;
  const payload = await verifySession(secret, token);
  return payload !== null;
}

export async function GET(
  req: Request,
  { params }: { params: { appName: string } },
) {
  if (!(await isAuthorized(req))) {
    return new Response("Unauthorized", { status: 401 });
  }
  const backend = process.env.VECTOR_BACKEND_URL;
  const apiKey = process.env.VECTOR_API_KEY;
  if (!backend || !apiKey) {
    return new Response("Server misconfigured", { status: 500 });
  }

  const upstream = await fetch(
    `${backend}/api/apps/${encodeURIComponent(params.appName)}/logs`,
    {
      headers: { "X-API-Key": apiKey, Accept: "text/event-stream" },
      cache: "no-store",
      signal: AbortSignal.any([AbortSignal.timeout(10_000), req.signal]),
    },
  );

  if (!upstream.ok || !upstream.body) {
    return new Response(`Upstream ${upstream.status}`, { status: upstream.status });
  }

  return new Response(upstream.body, {
    status: 200,
    headers: {
      "Content-Type": upstream.headers.get("content-type") ?? "text/event-stream",
      "Cache-Control":
        upstream.headers.get("cache-control") ?? "no-cache, no-transform",
      Connection: upstream.headers.get("connection") ?? "keep-alive",
      "X-Accel-Buffering": upstream.headers.get("x-accel-buffering") ?? "no",
    },
  });
}
