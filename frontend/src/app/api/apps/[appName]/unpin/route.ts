import { proxyToBackend } from "@/lib/proxy";

export async function POST(
  req: Request,
  { params }: { params: { appName: string } },
) {
  return proxyToBackend(
    req,
    `/api/apps/${encodeURIComponent(params.appName)}/unpin`,
    { method: "POST" },
  );
}
