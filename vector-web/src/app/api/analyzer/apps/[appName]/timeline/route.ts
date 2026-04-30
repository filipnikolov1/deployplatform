import { proxyToAnalyzer } from "@/lib/proxy";

export async function GET(
  req: Request,
  { params }: { params: { appName: string } },
) {
  const url = new URL(req.url);
  const qs = url.searchParams.toString();
  const path = `/api/analyzer/apps/${encodeURIComponent(params.appName)}/timeline${qs ? `?${qs}` : ""}`;
  return proxyToAnalyzer(req, path);
}
