"use client";

import { ExternalLink, Github } from "lucide-react";
import { formatRelative } from "@/lib/time";
import type { Deployment } from "@/types/deployment";

interface Props {
  app: Deployment;
  publicHost?: string;
}

export function AppInfoRow({ app, publicHost = "localhost" }: Props) {
  const publicUrl = `http://${app.appName}.${publicHost}`;
  return (
    <dl className="bg-white/[0.04] rounded-lg p-4 grid grid-cols-2 gap-x-6 gap-y-2 font-mono text-xs text-slate-400 tabular-nums sm:grid-cols-4">
      <div>
        <dt className="text-slate-400">image</dt>
        <dd className="truncate text-slate-200">{app.imageName}</dd>
      </div>
      <div>
        <dt className="text-slate-400">port</dt>
        <dd className="text-slate-200">{app.containerPort}</dd>
      </div>
      <div>
        <dt className="text-slate-400">url</dt>
        <dd>
          <a
            href={publicUrl}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-1 text-purple-300 hover:text-purple-200 focus:outline-none focus-visible:ring-focus"
          >
            {`${app.appName}.${publicHost}`} <ExternalLink size={12} />
          </a>
        </dd>
      </div>
      <div>
        <dt className="text-slate-400">repo</dt>
        <dd>
          <a
            href={app.repoUrl}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-1 text-purple-300 hover:text-purple-200 focus:outline-none focus-visible:ring-focus"
          >
            <Github size={12} /> github
          </a>
        </dd>
      </div>
      <div className="col-span-2 sm:col-span-4">
        <dt className="text-slate-400">updated</dt>
        <dd className="text-slate-200">{formatRelative(app.updatedAt)}</dd>
      </div>
    </dl>
  );
}
