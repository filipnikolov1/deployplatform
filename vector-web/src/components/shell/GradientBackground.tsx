export function GradientBackground() {
  return (
    <div
      aria-hidden="true"
      className="fixed inset-0 -z-10 overflow-hidden"
      style={{ background: "var(--c-bg-body)" }}
    >
      {/* Base gradient — near-black depth layer */}
      <div
        className="absolute inset-0"
        style={{ background: "linear-gradient(180deg, var(--c-bg-body) 0%, #000 100%)" }}
      />
      {/* Violet orb top-right */}
      <div
        className="absolute"
        style={{
          top: -200,
          right: -120,
          width: 560,
          height: 560,
          borderRadius: "50%",
          filter: "blur(120px)",
          background: "radial-gradient(circle, var(--c-accent-soft), transparent 70%)",
        }}
      />
      {/* Blue orb bottom-left — bespoke gradient stop, no token */}
      <div
        className="absolute"
        style={{
          bottom: -200,
          left: -120,
          width: 520,
          height: 520,
          borderRadius: "50%",
          filter: "blur(120px)",
          background: "radial-gradient(circle, rgba(59,130,246,0.10), transparent 70%)",
        }}
      />
    </div>
  );
}
