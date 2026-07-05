"use client";

import useSWR from "swr";

interface AppConfigResponse {
  appBaseDomain: string;
  scheme: string;
}

const fetcher = async (url: string): Promise<AppConfigResponse> => {
  const res = await fetch(url, { signal: AbortSignal.timeout(15_000) });
  if (!res.ok) throw new Error(`Failed to load app config: ${res.status}`);
  return res.json();
};

export function useAppConfig() {
  const { data, isLoading } = useSWR<AppConfigResponse>(
    "/api/config",
    fetcher,
    { revalidateOnFocus: false },
  );

  return {
    appBaseDomain: data?.appBaseDomain ?? "localhost",
    scheme: data?.scheme ?? "http",
    isLoading,
  };
}
