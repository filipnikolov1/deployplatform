"use client";

/**
 * SelfAppsSection — staggered grid of SelfAppCard components.
 * Wrapped in <Section> with "SELF-APPS" kicker.
 * Children fade-up at 0.05s stagger via MMOTION.list + MMOTION.item.
 */

import { motion } from "framer-motion";
import { Section } from "@/design/primitives/Section";
import { MMOTION } from "@/design/tokens";
import { SelfAppCard } from "./SelfAppCard";
import type { DeploymentEvent } from "@/types/vector";
import type { Deployment as DeploymentModel } from "@/types/deployment";

interface SelfAppsSectionProps {
  apps: DeploymentModel[];
  onOpen: (appName: string) => void;
  updateAvailableEvents: DeploymentEvent[];
  events: DeploymentEvent[];
}

export function SelfAppsSection({ apps, onOpen, updateAvailableEvents, events }: SelfAppsSectionProps) {
  if (!apps.length) return null;

  return (
    <Section
      title="SELF-APPS"
      count={apps.length}
      hint="Apps that run Vector itself"
    >
      <motion.div
        variants={MMOTION.list}
        initial="initial"
        animate="animate"
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))",
          gap: 12,
        }}
      >
        {apps.map((app) => {
          const updateEvent =
            updateAvailableEvents.find((e) => e.appName === app.appName) ?? null;
          return (
            <SelfAppCard
              key={app.appName}
              app={app}
              onOpen={onOpen}
              updateAvailableEvent={updateEvent}
              events={events}
            />
          );
        })}
      </motion.div>
    </Section>
  );
}
