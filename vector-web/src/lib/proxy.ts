import { verifySession, SESSION_COOKIE_NAME } from "@/lib/auth";

function getCookie(req: Request, name: string): string | undefined {
  const raw = req.headers.get("cookie") ?? "";
  for (const part of raw.split(";")) {
    const [k, ...v] = part.trim().split("=");
    if (k === name) return v.join("=");
  }
  return undefined;
}

async function isAuthorized(req: Request): Promise<boolean> {
  const token = getCookie(req, SESSION_COOKIE_NAME);
  if (!token) return false;
  const secret = process.env.SESSION_SECRET;
  if (!secret) return false;
  const payload = await verifySession(secret, token);
  return payload !== null;
}

export async function proxyToAnalyzer(
  req: Request,
  path: string,
  init: RequestInit = {},
): Promise<Response> {
  if (!(await isAuthorized(req))) {
    return new Response("Unauthorized", { status: 401 });
  }

  const analyzer = process.env.ANALYZER_URL;
  const apiKey = process.env.API_KEY;
  if (!analyzer || !apiKey) {
    return new Response("Server misconfigured", { status: 500 });
  }

  const headers: Record<string, string> = {};
  if (init.headers) {
    const existing = new Headers(init.headers);
    existing.forEach((value, key) => {
      headers[key] = value;
    });
  }
  headers["X-API-Key"] = apiKey;

  let upstream: Response;
  try {
    upstream = await fetch(`${analyzer}${path}`, {
      method: init.method ?? req.method,
      headers,
      body: init.body,
    });
  } catch {
    return Response.json(
      { error: "vector-analyzer unavailable" },
      { status: 503 },
    );
  }

  const responseHeaders = new Headers();
  const ct = upstream.headers.get("content-type");
  if (ct) responseHeaders.set("content-type", ct);

  return new Response(upstream.body, {
    status: upstream.status,
    headers: responseHeaders,
  });
}

export async function proxyToBackend(
  req: Request,
  path: string,
  init: RequestInit = {},
): Promise<Response> {
  if (!(await isAuthorized(req))) {
    return new Response("Unauthorized", { status: 401 });
  }

  const backend = process.env.BACKEND_URL;
  const apiKey = process.env.API_KEY;
  if (!backend || !apiKey) {
    return new Response("Server misconfigured", { status: 500 });
  }

  const headers: Record<string, string> = {};
  if (init.headers) {
    const existing = new Headers(init.headers);
    existing.forEach((value, key) => {
      headers[key] = value;
    });
  }
  headers["X-API-Key"] = apiKey;

  let upstream: Response;
  try {
    upstream = await fetch(`${backend}${path}`, {
      method: init.method ?? req.method,
      headers,
      body: init.body,
    });
  } catch {
    return Response.json(
      { error: "vector-api unavailable" },
      { status: 503 },
    );
  }

  const responseHeaders = new Headers();
  const ct = upstream.headers.get("content-type");
  if (ct) responseHeaders.set("content-type", ct);

  return new Response(upstream.body, {
    status: upstream.status,
    headers: responseHeaders,
  });
}
