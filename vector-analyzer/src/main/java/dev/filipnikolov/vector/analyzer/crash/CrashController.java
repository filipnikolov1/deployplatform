package dev.filipnikolov.vector.analyzer.crash;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/analyzer/apps/{appName}/crashes")
public class CrashController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CrashAnalysisService service;

    public CrashController(CrashAnalysisService service) {
        this.service = service;
    }

    @GetMapping("/{crashId}")
    public ResponseEntity<Map<String, Object>> getCrash(
            @PathVariable String appName,
            @PathVariable Long crashId) {
        Optional<CrashAnalysis> opt = service.findByAppAndCrashId(appName, crashId);
        if (opt.isEmpty()) {
            // Not yet generated — try to generate now (lazy backfill for crashes that pre-date Phase 5)
            CrashAnalysis fresh = service.generateForCrashEvent(crashId);
            if (fresh == null || !appName.equals(fresh.getAppName())) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(toResponse(fresh));
        }
        return ResponseEntity.ok(toResponse(opt.get()));
    }

    private Map<String, Object> toResponse(CrashAnalysis a) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", a.getId());
        r.put("appName", a.getAppName());
        r.put("crashEventId", a.getCrashEventId());
        r.put("suspectCommitSha", a.getSuspectCommitSha());
        r.put("lastGoodCommitSha", a.getLastGoodCommitSha());
        r.put("suspectFilePath", a.getSuspectFilePath());
        r.put("suspectLine", a.getSuspectLine());
        r.put("aiNarration", a.getAiNarration());
        r.put("aiProviderUsed", a.getAiProviderUsed());
        r.put("evidence", parseJson(a.getEvidenceJson()));
        r.put("signals", parseJson(a.getSignalsJson()));
        r.put("generatedAt", a.getGeneratedAt() != null ? a.getGeneratedAt().toString() : null);
        return r;
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, new TypeReference<Object>() {});
        } catch (Exception e) {
            return json;
        }
    }
}
