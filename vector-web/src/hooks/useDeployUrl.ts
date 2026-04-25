"use client";

import useSWR from "swr";

interface DeployUrlResponse {
  url: string;
}

const fetcher = async (url: string): Promise<DeployUrlResponse> => {
  const res = await fetch(url, { signal: AbortSignal.timeout(15_000) });
  if (!res.ok) throw new Error(`Failed to load deploy url: ${res.status}`);
  return res.json();
};

export function useDeployUrl() {
  const { data, isLoading } = useSWR<DeployUrlResponse>(
    "/api/setup/deploy-url",
    fetcher,
    { revalidateOnFocus: false },
  );

  return {
    url: data?.url ?? "",
    isLoading,
  };
}
