import { proxyToBackend } from "@/lib/proxy";

export async function POST(
  req: Request,
  { params }: { params: { appName: string } },
) {
  const body = await req.text();
  return proxyToBackend(
    req,
    `/api/apps/${encodeURIComponent(params.appName)}/rollback`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body,
    },
  );
}
