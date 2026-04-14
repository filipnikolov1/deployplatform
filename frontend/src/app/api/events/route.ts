import { proxyToBackend } from "@/lib/proxy";

export async function GET(req: Request) {
  const url = new URL(req.url);
  const qs = url.search;
  return proxyToBackend(req, `/api/events${qs}`);
}
