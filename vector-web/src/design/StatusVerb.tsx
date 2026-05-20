"use client";

/**
 * StatusVerb — uppercase mono colored word renderer.
 * Maps Vector DeploymentEventType (+ optional status for DEPLOY_FINISHED) to
 * a (verb, color) tuple and renders as an uppercase mono colored span.
 *
 * "Hidden" types (SELF_APP_BOOTSTRAPPED, WEBHOOK_IGNORED, DEPLOY_TRIGGERED,
 * DEPLOY_STARTED) return null — caller filters them out.
 */

import { CSSProperties } from "react";
import { M } from "@/design/tokens";
import type { DeploymentEventType, DeploymentEventStatus } from "@/types/vector";

interface VerbConfig {
  verb: string;
  color: string;
}

/** Returns null for hidden/collapsed event types. */
function resolveVerb(
  eventType: DeploymentEventType,
  status?: DeploymentEventStatus | null,
): VerbConfig | null {
  switch (eventType) {
    // DEPLOY_FINISHED branches on status
    case "DEPLOY_FINISHED":
      if (status === "SUCCESS") {
        return { verb: "DEPLOYED", color: M.ok };
      }
      if (status === "FAILURE") {
        return { verb: "FAILED", color: M.err };
      }
      // IN_PROGRESS — still in flight, show as PULLING
      return { verb: "PULLING", color: M.warn };

    case "FAILED":
      return { verb: "FAILED", color: M.err };

    case "CRASHED":
      return { verb: "CRASHED", color: M.err };

    case "RESTARTED":
      if (status === "FAILURE") return { verb: "RESTART FAILED", color: M.err };
      return { verb: "RESTARTED", color: M.ok };

    case "STOPPED":
      return { verb: "STOPPED", color: M.fg3 };

    case "MANUAL_ROLLBACK":
      return { verb: "ROLLED BACK", color: M.accent };

    case "UPDATE_AVAILABLE":
      return { verb: "UPDATE AVAILABLE", color: "#67E8F9" };

    case "UPDATE_TRIGGERED":
      return { verb: "UPDATING", color: "#67E8F9" };

    case "UPDATE_SUCCESS":
      return { verb: "UPDATED", color: M.ok };

    case "UPDATE_FAILED":
      return { verb: "UPDATE FAILED", color: M.err };

    case "UPDATER_UNREACHABLE":
      return { verb: "UPDATER OFFLINE", color: M.err };

    case "PIN_RELEASED":
      return { verb: "UNPINNED", color: M.fg3 };

    // Note: SUBDOMAIN_CHANGED is listed in the plan's verb table but is not
    // yet in the DeploymentEventType union. Handled by the default: null case below.

    // In-flight deploy lifecycle — collapsed into PULLING
    case "PULL_STARTED":
    case "PULL_FINISHED":
    case "CONTAINER_CREATING":
    case "CONTAINER_STARTED":
    case "BUILD_STARTED":
    case "BUILD_FINISHED":
    case "HEALTH_OK":
      return { verb: "PULLING", color: M.warn };

    // Hidden — caller should have filtered these out
    case "DEPLOY_TRIGGERED":
    case "DEPLOY_STARTED":
    case "SELF_APP_BOOTSTRAPPED":
    case "WEBHOOK_IGNORED":
      return null;

    default:
      return null;
  }
}

interface StatusVerbProps {
  eventType: DeploymentEventType;
  /** Required for DEPLOY_FINISHED to determine success vs failure */
  status?: DeploymentEventStatus | null;
  style?: CSSProperties;
  className?: string;
}

export function StatusVerb({ eventType, status, style, className }: StatusVerbProps) {
  const resolved = resolveVerb(eventType, status);

  if (!resolved) return null;

  return (
    <span
      style={{
        fontFamily: M.fontMono,
        fontSize: 11,
        fontWeight: 600,
        letterSpacing: "0.08em",
        textTransform: "uppercase",
        color: resolved.color,
        ...style,
      }}
      className={className}
    >
      {resolved.verb}
    </span>
  );
}

/** Hook-style utility for components that need the raw (verb, color) without rendering */
export function useStatusVerb(
  eventType: DeploymentEventType,
  status?: DeploymentEventStatus | null,
): VerbConfig | null {
  return resolveVerb(eventType, status);
}
