import type { Metadata } from "next";
import { Manrope, JetBrains_Mono } from "next/font/google";
import { GradientBackground } from "@/components/shell/GradientBackground";
import { Toaster } from "@/components/Toaster";
import "@/styles/globals.css";
import "@/design/colors_and_type.css";

const manrope = Manrope({ subsets: ["latin"], variable: "--font-manrope", display: "swap" });
const mono = JetBrains_Mono({ subsets: ["latin"], variable: "--font-mono", display: "swap" });

export const metadata: Metadata = {
  title: "Vector",
  description: "Ops dashboard for Vector deployments",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`${manrope.variable} ${mono.variable}`}>
      <body className="min-h-dvh antialiased">
        <GradientBackground />
        {children}
        <Toaster />
      </body>
    </html>
  );
}
