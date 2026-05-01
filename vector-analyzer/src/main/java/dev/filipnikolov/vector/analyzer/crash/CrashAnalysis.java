package dev.filipnikolov.vector.analyzer.crash;

import java.time.LocalDateTime;

public class CrashAnalysis {
    private Long id;
    private String appName;
    private Long crashEventId;
    private String suspectCommitSha;
    private String lastGoodCommitSha;
    private String suspectFilePath;
    private Integer suspectLine;
    private String aiNarration;
    private String aiProviderUsed;
    private String evidenceJson;
    private String signalsJson;
    private LocalDateTime generatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public Long getCrashEventId() { return crashEventId; }
    public void setCrashEventId(Long crashEventId) { this.crashEventId = crashEventId; }
    public String getSuspectCommitSha() { return suspectCommitSha; }
    public void setSuspectCommitSha(String suspectCommitSha) { this.suspectCommitSha = suspectCommitSha; }
    public String getLastGoodCommitSha() { return lastGoodCommitSha; }
    public void setLastGoodCommitSha(String lastGoodCommitSha) { this.lastGoodCommitSha = lastGoodCommitSha; }
    public String getSuspectFilePath() { return suspectFilePath; }
    public void setSuspectFilePath(String suspectFilePath) { this.suspectFilePath = suspectFilePath; }
    public Integer getSuspectLine() { return suspectLine; }
    public void setSuspectLine(Integer suspectLine) { this.suspectLine = suspectLine; }
    public String getAiNarration() { return aiNarration; }
    public void setAiNarration(String aiNarration) { this.aiNarration = aiNarration; }
    public String getAiProviderUsed() { return aiProviderUsed; }
    public void setAiProviderUsed(String aiProviderUsed) { this.aiProviderUsed = aiProviderUsed; }
    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }
    public String getSignalsJson() { return signalsJson; }
    public void setSignalsJson(String signalsJson) { this.signalsJson = signalsJson; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }
}
