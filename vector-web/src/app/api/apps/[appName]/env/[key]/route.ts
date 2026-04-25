import { proxyToBackend } from "@/lib/proxy";

export async function PUT(
  req: Request,
  { params }: { params: { appName: string; key: string } },
) {
  const value = await req.text();
  return proxyToBackend(
    req,
    `/api/apps/${encodeURIComponent(params.appName)}/env/${encodeURIComponent(params.key)}`,
    {
      method: "PUT",
      headers: { "Content-Type": "text/plain" },
      body: value,
    },
  );
}

export async function DELETE(
  req: Request,
  { params }: { params: { appName: string; key: string } },
) {
  return proxyToBackend(
    req,
    `/api/apps/${encodeURIComponent(params.appName)}/env/${encodeURIComponent(params.key)}`,
    { method: "DELETE" },
  );
}
