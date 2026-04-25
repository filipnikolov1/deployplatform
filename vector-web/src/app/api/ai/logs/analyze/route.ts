import { proxyToBackend } from "@/lib/proxy";

export const dynamic = "force-dynamic";

export async function GET(req: Request) {
  const url = new URL(req.url);
  const app = url.searchParams.get("app") ?? "";
  return proxyToBackend(
    req,
    `/api/ai/logs/analyze?app=${encodeURIComponent(app)}`,
  );
}
