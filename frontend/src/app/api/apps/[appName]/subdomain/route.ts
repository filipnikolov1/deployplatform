import { proxyToBackend } from "@/lib/proxy";

export async function PATCH(
  req: Request,
  { params }: { params: { appName: string } },
) {
  const body = await req.text();
  return proxyToBackend(
    req,
    `/api/apps/${encodeURIComponent(params.appName)}/subdomain`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body,
    },
  );
}
