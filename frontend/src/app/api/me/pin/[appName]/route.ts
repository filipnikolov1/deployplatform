import { proxyToBackend } from "@/lib/proxy";

export async function POST(
  req: Request,
  { params }: { params: { appName: string } },
) {
  return proxyToBackend(
    req,
    `/api/me/pin/${encodeURIComponent(params.appName)}`,
    { method: "POST" },
  );
}

export async function DELETE(
  req: Request,
  { params }: { params: { appName: string } },
) {
  return proxyToBackend(
    req,
    `/api/me/pin/${encodeURIComponent(params.appName)}`,
    { method: "DELETE" },
  );
}
