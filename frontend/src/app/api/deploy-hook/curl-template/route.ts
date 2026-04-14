import { proxyToBackend } from "@/lib/proxy";

export async function GET(req: Request) {
  const url = new URL(req.url);
  return proxyToBackend(req, `/api/deploy-hook/curl-template${url.search}`);
}
