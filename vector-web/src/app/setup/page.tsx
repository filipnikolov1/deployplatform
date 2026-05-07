"use client";

import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { AppShell } from "@/components/shell/AppShell";
import { PageHeader } from "@/design/primitives";
import { Stepper } from "@/design/Stepper";
import type { StepItem } from "@/design/Stepper";
import { M, MMOTION } from "@/design/tokens";
import { EnvironmentPanel } from "@/components/setup/EnvironmentPanel";
import { StepConfigure } from "@/components/setup/StepConfigure";
import type { Lang } from "@/components/setup/StepConfigure";
import { StepCi } from "@/components/setup/StepCi";
import { StepDockerfile } from "@/components/setup/StepDockerfile";
import { StepDeploy } from "@/components/setup/StepDeploy";

const STEPS: StepItem[] = [
  { id: "configure", label: "Configure" },
  { id: "ci", label: "CI" },
  { id: "dockerfile", label: "Dockerfile" },
  { id: "deploy", label: "Deploy" },
];

const STEP_TITLES: string[] = [
  "Configure your app",
  "GitHub Actions workflow",
  "Dockerfile",
  "Deploy",
];

const STEP_SUBTITLES: string[] = [
  "Pick your language, set an app name, and copy your repo secrets.",
  "Generated for your language and branch — save to .github/workflows/deploy.yml.",
  "Generated Dockerfile for your language — save to your repo root.",
  "Push a commit and watch Vector deploy your app.",
];

export default function SetupPage() {
  const [step, setStep] = useState(0);
  const [lang, setLang] = useState<Lang>("node");
  const [appName, setAppName] = useState("my-app");
  const [branch, setBranch] = useState("main");

  const goNext = () => setStep((s) => Math.min(s + 1, STEPS.length - 1));
  const goBack = () => setStep((s) => Math.max(s - 1, 0));

  return (
    <AppShell>
      <PageHeader
        kicker="GET STARTED"
        title="Setup"
        subtitle="Three steps, three minutes. We'll check your environment as you go."
      />

      {/* Stepper */}
      <div style={{ marginBottom: 40 }}>
        <Stepper steps={STEPS} currentIndex={step} size="large" />
      </div>

      {/* Step header */}
      <div style={{ marginBottom: 28 }}>
        <div
          style={{
            fontSize: 20,
            fontWeight: 600,
            color: M.fg,
            fontFamily: M.fontSans,
            letterSpacing: "-0.02em",
            marginBottom: 6,
          }}
        >
          {STEP_TITLES[step]}
        </div>
        <div
          style={{
            fontSize: 14,
            color: M.fg3,
            fontFamily: M.fontSans,
            lineHeight: 1.5,
          }}
        >
          {STEP_SUBTITLES[step]}
        </div>
      </div>

      {/* Two-column layout */}
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "1fr 300px",
          gap: 40,
          alignItems: "start",
        }}
      >
        {/* Main step content */}
        <div
          style={{
            background: M.surface,
            border: `1px solid ${M.line}`,
            borderRadius: M.rLg,
            padding: 32,
          }}
        >
          <AnimatePresence mode="wait">
            <motion.div
              key={step}
              initial={MMOTION.page.initial}
              animate={MMOTION.page.animate}
              exit={MMOTION.page.exit}
              transition={MMOTION.page.transition}
            >
              {step === 0 && (
                <StepConfigure
                  lang={lang}
                  appName={appName}
                  branch={branch}
                  onLangChange={setLang}
                  onAppNameChange={setAppName}
                  onBranchChange={setBranch}
                  onNext={goNext}
                />
              )}
              {step === 1 && (
                <StepCi
                  lang={lang}
                  appName={appName}
                  branch={branch}
                  onBack={goBack}
                  onNext={goNext}
                />
              )}
              {step === 2 && (
                <StepDockerfile
                  lang={lang}
                  appName={appName}
                  branch={branch}
                  onBack={goBack}
                  onNext={goNext}
                />
              )}
              {step === 3 && (
                <StepDeploy
                  appName={appName}
                  onBack={goBack}
                />
              )}
            </motion.div>
          </AnimatePresence>
        </div>

        {/* Environment panel — sticky right column */}
        <EnvironmentPanel />
      </div>
    </AppShell>
  );
}
