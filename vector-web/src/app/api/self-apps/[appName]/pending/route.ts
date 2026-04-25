import { proxyToBackend } from "@/lib/proxy";

export async function GET(
  req: Request,
  { params }: { params: { appName: string } },
) {
  return proxyToBackend(
    req,
    `/api/self-apps/${encodeURIComponent(params.appName)}/pending`,
    { method: "GET" },
  );
}
