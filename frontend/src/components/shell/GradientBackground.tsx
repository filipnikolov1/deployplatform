export function GradientBackground() {
  return (
    <div
      aria-hidden="true"
      className="fixed inset-0 -z-10 overflow-hidden bg-gradient-to-b from-bg-base to-bg-deep"
    >
      <div
        className="absolute -top-40 -right-40 h-[520px] w-[520px] rounded-full blur-3xl"
        style={{ background: "radial-gradient(circle, rgba(124,58,237,0.25), transparent 70%)" }}
      />
      <div
        className="absolute -bottom-40 -left-40 h-[480px] w-[480px] rounded-full blur-3xl"
        style={{ background: "radial-gradient(circle, rgba(59,130,246,0.20), transparent 70%)" }}
      />
    </div>
  );
}
