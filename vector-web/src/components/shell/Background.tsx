"use client";

/**
 * Background — ported from MBackground in the design handoff.
 * Single dim radial vignette at top-right + two motion.divs drifting slowly.
 * Flat near-black base (M.bg = #08080A). Render once at app root, behind everything.
 *
 * prefers-reduced-motion: the opacity animations are controlled by the global
 * CSS rule in globals.css which collapses animation-duration to 0.001ms, so
 * the motion.divs will hold their starting opacity and not flicker.
 */

import { motion } from "framer-motion";
import { M } from "@/design/tokens";

export function Background() {
  return (
    <div
      aria-hidden="true"
      style={{
        position: "fixed",
        inset: 0,
        zIndex: -1,
        overflow: "hidden",
        background: M.bg,
      }}
    >
      {/* Violet orb — top-right */}
      <motion.div
        animate={{ opacity: [0.35, 0.55, 0.35] }}
        transition={{ duration: 8, repeat: Infinity, ease: "easeInOut" }}
        style={{
          position: "absolute",
          top: -300,
          right: -200,
          width: 640,
          height: 640,
          borderRadius: "50%",
          background: `radial-gradient(circle, ${M.accent}26 0%, transparent 60%)`,
          filter: "blur(40px)",
        }}
      />
      {/* Blue orb — bottom-left */}
      <motion.div
        animate={{ opacity: [0.18, 0.32, 0.18] }}
        transition={{
          duration: 10,
          repeat: Infinity,
          ease: "easeInOut",
          delay: 2,
        }}
        style={{
          position: "absolute",
          bottom: -200,
          left: -100,
          width: 480,
          height: 480,
          borderRadius: "50%",
          background: "radial-gradient(circle, #60A5FA1A 0%, transparent 60%)",
          filter: "blur(40px)",
        }}
      />
    </div>
  );
}
