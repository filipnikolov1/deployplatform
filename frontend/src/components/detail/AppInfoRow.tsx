"use client";

import { type ReactNode } from "react";
import { ExternalLink, GitBranch } from "lucide-react";
import { formatRelative } from "@/lib/time";
import type { Deployment } from "@/types/deployment";
import type { CommitsAhead } from "@/types/launchpad";

interface Props {
  app: Deployment;
  publicHost: string;
  commitsAhead?: CommitsAhead;
}

export function AppInfoRow({ app, publicHost, commitsAhead }: Props) {
  const publicUrl = `http://${app.appName}.${publicHost}`;
  const publicHostLabel = `${app.appName}.${publicHost}`;
  const [imageRepo, imageTag = "latest"] = (app.imageName ?? "").split(":");

  const commitUrl =
    app.commitSha && app.repoUrl
      ? `${app.repoUrl.replace(/\.git$/, "")}/commit/${app.commitSha}`
      : "#";

  return (
    <div className="mx-6 mt-4 space-y-3">
      {/* Row A: deployment identity */}
      <dl className="grid grid-cols-2 gap-x-6 gap-y-2 rounded-lg border border-white/[0.06] bg-white/[0.03] p-4 text-xs sm:grid-cols-4">
        <InfoCell label="image" mono>
          {imageRepo || "—"}
        </InfoCell>
        <InfoCell label="tag" mono>
          {imageTag}
        </InfoCell>
        <InfoCell label="port" mono>
          {app.containerPort}
        </InfoCell>
        <InfoCell label="url">
          <a
            href={publicUrl}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-1 text-purple-300 hover:text-purple-200 focus:outline-none focus-visible:ring-focus"
          >
            {publicHostLabel} <ExternalLink size={11} />
          </a>
        </InfoCell>
      </dl>

      {/* Row B: current commit */}
      <dl className="grid grid-cols-2 gap-x-6 gap-y-2 rounded-lg border border-white/[0.06] bg-white/[0.03] p-4 text-xs sm:grid-cols-4">
        <InfoCell label="branch">
          <span className="inline-flex items-center gap-1 text-slate-200">
            <GitBranch className="h-3 w-3" /> {app.branch ?? "—"}
          </span>
        </InfoCell>
        <InfoCell label="commit" mono>
          {app.commitSha ? (
            <a
              href={commitUrl}
              target="_blank"
              rel="noreferrer"
              className="text-purple-300 hover:text-purple-200"
            >
              {app.commitSha.slice(0, 7)}
            </a>
          ) : (
            "—"
          )}
        </InfoCell>
        <InfoCell label="deployed">
          {formatRelative(app.updatedAt)}
        </InfoCell>
        <InfoCell label="ahead">
          {commitsAhead?.count == null ? (
            <span className="text-slate-500">—</span>
          ) : commitsAhead.count === 0 ? (
            <span className="text-emerald-300">up to date</span>
          ) : (
            <a
              href={commitsAhead.compareUrl}
              target="_blank"
              rel="noreferrer"
              className="text-amber-300 hover:text-amber-200"
            >
              {commitsAhead.count} commits
            </a>
          )}
        </InfoCell>
        {app.commitMessage && (
          <div className="col-span-2 sm:col-span-4">
            <dt className="text-[10px] uppercase tracking-[0.14em] text-slate-500">
              message
            </dt>
            <dd className="truncate text-slate-300 font-sans">
              {app.commitMessage}
            </dd>
          </div>
        )}
      </dl>
    </div>
  );
}

function InfoCell({
  label,
  mono,
  children,
}: {
  label: string;
  mono?: boolean;
  children: ReactNode;
}) {
  return (
    <div className="min-w-0">
      <dt className="text-[10px] uppercase tracking-[0.14em] text-slate-500">
        {label}
      </dt>
      <dd
        className={`truncate ${mono ? "font-mono" : "font-sans"} text-slate-200`}
      >
        {children}
      </dd>
    </div>
  );
}
