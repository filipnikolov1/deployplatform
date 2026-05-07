import { proxyToAnalyzer } from "@/lib/proxy";

export async function GET(
  req: Request,
  { params }: { params: { appName: string } },
) {
  const url = new URL(req.url);
  const days = url.searchParams.get("days") ?? "30";
  return proxyToAnalyzer(
    req,
    `/api/analyzer/apps/${encodeURIComponent(params.appName)}/uptime?days=${days}`,
  );
}
