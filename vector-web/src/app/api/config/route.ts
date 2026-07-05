// Runtime platform config for client components. Replaces the build-time
// NEXT_PUBLIC_APP_BASE_DOMAIN inlining so the web image is environment-generic:
// the same image works on any domain (k8s prerequisite).
export async function GET() {
  const domain = (process.env.VECTOR_DOMAIN || "localhost").trim();
  const rawNs = process.env.VECTOR_APP_NAMESPACE;
  // unset → default "apps"; explicitly blank → apps on the root domain
  const ns = rawNs === undefined ? "apps" : rawNs.trim();
  const appBaseDomain = ns === "" ? domain : `${ns}.${domain}`;
  const scheme = domain === "localhost" ? "http" : "https";
  return Response.json({ appBaseDomain, scheme });
}
