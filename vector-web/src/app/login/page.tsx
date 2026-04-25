import { LoginCard } from "@/components/login/LoginCard";

export default function LoginPage() {
  return (
    <main className="relative flex min-h-dvh items-center justify-center overflow-hidden p-6">
      <div className="pointer-events-none absolute inset-0">
        <div className="absolute left-[10%] top-[12%] h-56 w-56 rounded-full bg-sky-300/10 blur-3xl" />
        <div className="absolute bottom-[8%] right-[12%] h-72 w-72 rounded-full bg-violet-400/12 blur-3xl" />
      </div>
      <LoginCard />
    </main>
  );
}
