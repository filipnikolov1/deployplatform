import { proxyToAnalyzer } from "@/lib/proxy";

export async function GET(
  req: Request,
  { params }: { params: { appName: string } },
) {
  const url = new URL(req.url);
  const path = url.searchParams.get("path") ?? "";
  const limit = url.searchParams.get("limit") ?? "20";
  return proxyToAnalyzer(
    req,
    `/api/analyzer/apps/${encodeURIComponent(params.appName)}/commits/file-history?path=${encodeURIComponent(path)}&limit=${encodeURIComponent(limit)}`,
  );
}
