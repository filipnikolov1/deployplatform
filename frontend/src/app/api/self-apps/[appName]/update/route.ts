import { proxyToBackend } from "@/lib/proxy";

export async function POST(
  req: Request,
  { params }: { params: { appName: string } },
) {
  return proxyToBackend(
    req,
    `/api/self-apps/${encodeURIComponent(params.appName)}/update`,
    { method: "POST" },
  );
}
