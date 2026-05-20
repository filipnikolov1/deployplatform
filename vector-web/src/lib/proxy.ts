import { getSessionToken, verifySession } from "@/lib/auth";

const FORWARDABLE_HEADERS = new Set(["content-type", "accept"]);
const PROXY_TIMEOUT_MS = 30_000;

async function isAuthorized(req: Request): Promise<boolean> {
  const token = getSessionToken(req);
  if (!token) return false;
  const secret = process.env.SESSION_SECRET;
  if (!secret) return false;
  const payload = await verifySession(secret, token);
  return payload !== null;
}

function buildHeaders(init: RequestInit, apiKey: string): Record<string, string> {
  const headers: Record<string, string> = {};
  if (init.headers) {
    const existing = new Headers(init.headers);
    existing.forEach((value, key) => {
      // Whitelist forwarded headers — never blind-copy caller headers into the
      // privileged API-key request (defence in depth against header injection).
      if (FORWARDABLE_HEADERS.has(key.toLowerCase())) {
        headers[key] = value;
      }
    });
  }
  headers["X-API-Key"] = apiKey;
  return headers;
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

  let upstream: Response;
  try {
    upstream = await fetch(`${analyzer}${path}`, {
      method: init.method ?? req.method,
      headers: buildHeaders(init, apiKey),
      body: init.body,
      signal: AbortSignal.timeout(PROXY_TIMEOUT_MS),
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

  let upstream: Response;
  try {
    upstream = await fetch(`${backend}${path}`, {
      method: init.method ?? req.method,
      headers: buildHeaders(init, apiKey),
      body: init.body,
      signal: AbortSignal.timeout(PROXY_TIMEOUT_MS),
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
