import { proxyToAnalyzer } from "@/lib/proxy";

export async function GET(
  req: Request,
  { params }: { params: { appName: string } },
) {
  return proxyToAnalyzer(
    req,
    `/api/analyzer/apps/${encodeURIComponent(params.appName)}/deploys`,
  );
}
