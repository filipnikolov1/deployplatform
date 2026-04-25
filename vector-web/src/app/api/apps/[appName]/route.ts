import { proxyToBackend } from "@/lib/proxy";

export async function GET(
  req: Request,
  { params }: { params: { appName: string } },
) {
  return proxyToBackend(req, `/api/apps/${encodeURIComponent(params.appName)}`);
}

export async function DELETE(
  req: Request,
  { params }: { params: { appName: string } },
) {
  return proxyToBackend(
    req,
    `/api/apps/${encodeURIComponent(params.appName)}`,
    { method: "DELETE" },
  );
}
