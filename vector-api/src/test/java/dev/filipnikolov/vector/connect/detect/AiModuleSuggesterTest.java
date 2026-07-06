package dev.filipnikolov.vector.connect.detect;

import dev.filipnikolov.vector.ai.AiProvider;
import dev.filipnikolov.vector.ai.AiProviderException;
import dev.filipnikolov.vector.ai.AiRequest;
import dev.filipnikolov.vector.ai.AiResponse;
import dev.filipnikolov.vector.github.client.dto.StackKind;
import dev.filipnikolov.vector.github.scan.RepoScan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiModuleSuggesterTest {

    private AiProvider aiProvider;
    private AiModuleSuggester suggester;

    @BeforeEach
    void setUp() {
        aiProvider = Mockito.mock(AiProvider.class);
        suggester = new AiModuleSuggester(aiProvider);
    }

    private RepoScan scanOf(List<String> treePaths) {
        return new RepoScan(treePaths, false, List.of(), Optional.empty());
    }

    @Test
    void doesNotCallAiWhenExactlyOneDeterministicCandidate() {
        List<ModuleCandidate> deterministic = List.of(
                new ModuleCandidate("", StackKind.node, BuildMode.BUILDPACK, 3000, 1.0));

        List<ModuleCandidate> result = suggester.refine(scanOf(List.of("package.json")), deterministic);

        assertThat(result).isEqualTo(deterministic);
        verify(aiProvider, never()).analyze(any());
    }

    @Test
    void callsAiWhenDeterministicIsEmpty() {
        RepoScan scan = scanOf(List.of("weird.txt"));
        when(aiProvider.analyze(any())).thenReturn(new AiResponse(
                "[{\"path\":\"\",\"stack\":\"node\",\"port\":3000,\"include\":true}]", "test"));

        List<ModuleCandidate> result = suggester.refine(scan, List.of());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).path()).isEqualTo("");
        assertThat(result.get(0).stack()).isEqualTo(StackKind.node);
        assertThat(result.get(0).portGuess()).isEqualTo(3000);
    }

    @Test
    void callsAiWhenMultipleDeterministicCandidates() {
        RepoScan scan = scanOf(List.of("apps/web/package.json", "apps/api/go.mod"));
        List<ModuleCandidate> deterministic = List.of(
                new ModuleCandidate("apps/web", StackKind.node, BuildMode.BUILDPACK, 3000, 1.0),
                new ModuleCandidate("apps/api", StackKind.go, BuildMode.BUILDPACK, 8080, 1.0));
        when(aiProvider.analyze(any())).thenReturn(new AiResponse(
                "[{\"path\":\"apps/web\",\"stack\":\"node\",\"port\":3000,\"include\":true}," +
                "{\"path\":\"apps/api\",\"stack\":\"go\",\"port\":8080,\"include\":true}]", "test"));

        suggester.refine(scan, deterministic);

        verify(aiProvider).analyze(any());
    }

    @Test
    void aiCanDropDeterministicCandidateViaIncludeFalse() {
        RepoScan scan = scanOf(List.of("apps/web/package.json", "apps/api/go.mod"));
        List<ModuleCandidate> deterministic = List.of(
                new ModuleCandidate("apps/web", StackKind.node, BuildMode.BUILDPACK, 3000, 1.0),
                new ModuleCandidate("apps/api", StackKind.go, BuildMode.BUILDPACK, 8080, 1.0));
        when(aiProvider.analyze(any())).thenReturn(new AiResponse(
                "[{\"path\":\"apps/web\",\"stack\":\"node\",\"port\":3000,\"include\":true}," +
                "{\"path\":\"apps/api\",\"stack\":\"go\",\"port\":8080,\"include\":false}]", "test"));

        List<ModuleCandidate> result = suggester.refine(scan, deterministic);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).path()).isEqualTo("apps/web");
    }

    @Test
    void aiCanAdjustPortGuess() {
        RepoScan scan = scanOf(List.of("apps/web/package.json", "apps/api/go.mod"));
        List<ModuleCandidate> deterministic = List.of(
                new ModuleCandidate("apps/web", StackKind.node, BuildMode.BUILDPACK, 3000, 1.0),
                new ModuleCandidate("apps/api", StackKind.go, BuildMode.BUILDPACK, 8080, 1.0));
        when(aiProvider.analyze(any())).thenReturn(new AiResponse(
                "[{\"path\":\"apps/web\",\"stack\":\"node\",\"port\":4000,\"include\":true}," +
                "{\"path\":\"apps/api\",\"stack\":\"go\",\"port\":8080,\"include\":true}]", "test"));

        List<ModuleCandidate> result = suggester.refine(scan, deterministic);

        ModuleCandidate web = result.stream().filter(c -> c.path().equals("apps/web")).findFirst().orElseThrow();
        assertThat(web.portGuess()).isEqualTo(4000);
    }

    @Test
    void aiCanAddCandidateOnlyAtExistingTreePath() {
        RepoScan scan = scanOf(List.of("apps/web/package.json", "apps/worker/go.mod"));
        List<ModuleCandidate> deterministic = List.of(
                new ModuleCandidate("apps/web", StackKind.node, BuildMode.BUILDPACK, 3000, 1.0),
                new ModuleCandidate("apps/worker", StackKind.go, BuildMode.BUILDPACK, 8080, 1.0));
        when(aiProvider.analyze(any())).thenReturn(new AiResponse(
                "[{\"path\":\"apps/web\",\"stack\":\"node\",\"port\":3000,\"include\":true}," +
                "{\"path\":\"apps/worker\",\"stack\":\"go\",\"port\":8080,\"include\":true}," +
                "{\"path\":\"apps/extra\",\"stack\":\"node\",\"port\":3001,\"include\":true}]", "test"));

        List<ModuleCandidate> result = suggester.refine(scan, deterministic);

        assertThat(result).extracting(ModuleCandidate::path).doesNotContain("apps/extra");
    }

    @Test
    void aiAddAtNonexistentTreePathIsIgnoredWhenPathExistsInTree() {
        RepoScan scan = scanOf(List.of("apps/web/package.json", "apps/api/go.mod", "apps/api/extra/marker.txt"));
        List<ModuleCandidate> deterministic = List.of(
                new ModuleCandidate("apps/web", StackKind.node, BuildMode.BUILDPACK, 3000, 1.0),
                new ModuleCandidate("apps/api", StackKind.go, BuildMode.BUILDPACK, 8080, 1.0));
        when(aiProvider.analyze(any())).thenReturn(new AiResponse(
                "[{\"path\":\"apps/web\",\"stack\":\"node\",\"port\":3000,\"include\":true}," +
                "{\"path\":\"apps/api\",\"stack\":\"go\",\"port\":8080,\"include\":true}," +
                "{\"path\":\"apps/api/extra\",\"stack\":\"node\",\"port\":3001,\"include\":true}]", "test"));

        List<ModuleCandidate> result = suggester.refine(scan, deterministic);

        assertThat(result).extracting(ModuleCandidate::path).contains("apps/api/extra");
    }

    @Test
    void malformedAiResponseReturnsDeterministicUnchanged() {
        RepoScan scan = scanOf(List.of("weird.txt"));
        when(aiProvider.analyze(any())).thenReturn(new AiResponse("not json", "test"));

        List<ModuleCandidate> result = suggester.refine(scan, List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void providerExceptionReturnsDeterministicUnchangedPassthrough() {
        RepoScan scan = scanOf(List.of("apps/web/package.json", "apps/api/go.mod"));
        List<ModuleCandidate> deterministic = List.of(
                new ModuleCandidate("apps/web", StackKind.node, BuildMode.BUILDPACK, 3000, 1.0),
                new ModuleCandidate("apps/api", StackKind.go, BuildMode.BUILDPACK, 8080, 1.0));
        when(aiProvider.analyze(any())).thenThrow(new AiProviderException("boom"));

        List<ModuleCandidate> result = suggester.refine(scan, deterministic);

        assertThat(result).isEqualTo(deterministic);
    }
}
