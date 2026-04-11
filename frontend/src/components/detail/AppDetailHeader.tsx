"use client";

import { RotateCcw, Square, X } from "lucide-react";
import { Button } from "@/components/primitives/Button";
import { StatusDot } from "@/components/primitives/StatusDot";
import type { Deployment } from "@/types/deployment";

interface Props {
  app: Deployment;
  onClose: () => void;
  titleId: string;
}

export function AppDetailHeader({ app, onClose, titleId }: Props) {
  return (
    <div className="flex items-center gap-3 border-b border-white/10 p-4">
      <StatusDot status={app.status} />
      <h2 id={titleId} className="flex-1 text-lg font-semibold text-slate-100">
        {app.appName}
      </h2>
      <Button variant="ghost-purple" disabled leadingIcon={<RotateCcw size={16} />}>
        Restart
      </Button>
      <Button variant="ghost-red" disabled leadingIcon={<Square size={16} />}>
        Stop
      </Button>
      <Button variant="icon" aria-label="Close" onClick={onClose}>
        <X size={20} />
      </Button>
    </div>
  );
}
