import { proxyToBackend } from "@/lib/proxy";

export async function GET(
  req: Request,
  { params }: { params: { appName: string } },
) {
  const url = new URL(req.url);
  const qs = url.search;
  return proxyToBackend(
    req,
    `/api/apps/${encodeURIComponent(params.appName)}/events${qs}`,
  );
}
