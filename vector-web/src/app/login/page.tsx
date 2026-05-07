import { LoginCard } from "@/components/login/LoginCard";

export default function LoginPage() {
  return (
    <main
      style={{
        minHeight: "100dvh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: 24,
      }}
    >
      <LoginCard />
    </main>
  );
}
