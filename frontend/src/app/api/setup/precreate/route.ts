import { proxyToBackend } from "@/lib/proxy";

export async function POST(req: Request) {
  const body = await req.text();
  return proxyToBackend(req, "/api/setup/precreate", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body,
  });
}
