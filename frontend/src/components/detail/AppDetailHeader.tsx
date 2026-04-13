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
    <div className="flex items-center justify-between p-6 border-b border-white/[0.08]">
      <div className="flex items-center gap-3">
      <StatusDot status={app.status} />
        <h2 id={titleId} className="text-lg font-semibold text-slate-100">
          {app.appName}
        </h2>
      </div>
      <div className="flex items-center gap-2">
        <Button variant="ghost-purple" disabled leadingIcon={<RotateCcw size={16} />}>
          Restart
        </Button>
        <Button variant="ghost-red" disabled leadingIcon={<Square size={16} />}>
          Stop
        </Button>
        <Button
          variant="icon"
          aria-label="Close"
          onClick={onClose}
          className="h-11 w-11 md:h-8 md:w-8 rounded-lg hover:bg-white/10"
        >
        <X size={20} />
        </Button>
      </div>
    </div>
  );
}
