import { proxyToAnalyzer } from "@/lib/proxy";

export async function GET(
  req: Request,
  { params }: { params: { appName: string; sha: string } },
) {
  const url = new URL(req.url);
  const path = url.searchParams.get("path") ?? "";
  return proxyToAnalyzer(
    req,
    `/api/analyzer/apps/${encodeURIComponent(params.appName)}/commits/${encodeURIComponent(params.sha)}/files?path=${encodeURIComponent(path)}`,
  );
}
