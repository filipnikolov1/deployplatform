import { proxyToBackend } from "@/lib/proxy";

export async function GET(req: Request) {
  const url = new URL(req.url);
  return proxyToBackend(req, `/api/setup/templates/dockerfile${url.search}`);
}
