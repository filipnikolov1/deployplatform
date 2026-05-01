import { proxyToAnalyzer } from "@/lib/proxy";

export async function GET(
  req: Request,
  { params }: { params: { appName: string; crashId: string } },
) {
  return proxyToAnalyzer(
    req,
    `/api/analyzer/apps/${encodeURIComponent(params.appName)}/crashes/${encodeURIComponent(params.crashId)}`,
  );
}
