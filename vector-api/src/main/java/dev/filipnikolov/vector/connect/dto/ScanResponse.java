package dev.filipnikolov.vector.connect.dto;

import dev.filipnikolov.vector.connect.detect.DbSuggestion;
import dev.filipnikolov.vector.connect.detect.ModuleCandidate;

import java.util.List;

public record ScanResponse(List<ScannedModule> modules, DbSuggestion db, boolean truncated,
                            boolean composeUsedAsSignalOnly) {

    public record ScannedModule(ModuleCandidate candidate, String suggestedAppName, String suggestedSubdomain) {}
}
