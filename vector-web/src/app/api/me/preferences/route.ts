import { proxyToBackend } from "@/lib/proxy";

export async function PATCH(req: Request) {
  const body = await req.text();
  return proxyToBackend(req, "/api/me/preferences", {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body,
  });
}
