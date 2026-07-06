package dev.filipnikolov.vector.connect.detect;

import dev.filipnikolov.vector.github.scan.ManifestFile;
import dev.filipnikolov.vector.github.scan.RepoScan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DbDetectorTest {

    private final DbDetector detector = new DbDetector();

    private RepoScan scanOf(List<String> treePaths, List<ManifestFile> manifests, String composeYaml) {
        return new RepoScan(treePaths, false, manifests, Optional.ofNullable(composeYaml));
    }

    @Test
    void noSignalsYieldsNone() {
        RepoScan scan = scanOf(List.of("package.json"),
                List.of(new ManifestFile("package.json", "{\"dependencies\":{}}")), null);

        DbSuggestion suggestion = detector.suggest(scan);

        assertThat(suggestion.likelihood()).isEqualTo(Likelihood.NONE);
        assertThat(suggestion.signals()).isEmpty();
    }

    @Test
    void prismaDirSignalYieldsLikely() {
        RepoScan scan = scanOf(List.of("prisma/schema.prisma"), List.of(), null);

        DbSuggestion suggestion = detector.suggest(scan);

        assertThat(suggestion.likelihood()).isEqualTo(Likelihood.LIKELY);
        assertThat(suggestion.signals()).hasSize(1);
    }

    @Test
    void prismaClientDependencySignalYieldsLikely() {
        RepoScan scan = scanOf(List.of("package.json"),
                List.of(new ManifestFile("package.json", "{\"dependencies\":{\"@prisma/client\":\"5.0.0\"}}")), null);

        DbSuggestion suggestion = detector.suggest(scan);

        assertThat(suggestion.likelihood()).isEqualTo(Likelihood.LIKELY);
    }

    @Test
    void databaseUrlInManifestSignalYieldsLikely() {
        RepoScan scan = scanOf(List.of(".env.example"),
                List.of(new ManifestFile(".env.example", "DATABASE_URL=postgres://localhost/db")), null);

        DbSuggestion suggestion = detector.suggest(scan);

        assertThat(suggestion.likelihood()).isEqualTo(Likelihood.LIKELY);
    }

    @Test
    void springDatasourceUrlSignalYieldsLikely() {
        RepoScan scan = scanOf(List.of("application.properties"),
                List.of(new ManifestFile("application.properties", "SPRING_DATASOURCE_URL=jdbc:h2:mem:testdb")), null);

        DbSuggestion suggestion = detector.suggest(scan);

        assertThat(suggestion.likelihood()).isEqualTo(Likelihood.LIKELY);
    }

    @Test
    void migrationsDirTreePathSignalYieldsLikely() {
        RepoScan scan = scanOf(List.of("migrations/001_init.sql"), List.of(), null);

        DbSuggestion suggestion = detector.suggest(scan);

        assertThat(suggestion.likelihood()).isEqualTo(Likelihood.LIKELY);
    }

    @Test
    void dbMigrateDirTreePathSignalYieldsLikely() {
        RepoScan scan = scanOf(List.of("db/migrate/001_init.rb"), List.of(), null);

        DbSuggestion suggestion = detector.suggest(scan);

        assertThat(suggestion.likelihood()).isEqualTo(Likelihood.LIKELY);
    }

    @Test
    void pgDriverDependencySignalYieldsLikely() {
        RepoScan scan = scanOf(List.of("package.json"),
                List.of(new ManifestFile("package.json", "{\"dependencies\":{\"pg\":\"8.0.0\"}}")), null);

        DbSuggestion suggestion = detector.suggest(scan);

        assertThat(suggestion.likelihood()).isEqualTo(Likelihood.LIKELY);
    }

    @Test
    void postgresqlDriverDependencySignalYieldsLikely() {
        RepoScan scan = scanOf(List.of("pom.xml"),
                List.of(new ManifestFile("pom.xml", "<artifactId>postgresql</artifactId>")), null);

        DbSuggestion suggestion = detector.suggest(scan);

        assertThat(suggestion.likelihood()).isEqualTo(Likelihood.LIKELY);
    }

    @Test
    void composePostgresImageSignalYieldsLikely() {
        RepoScan scan = scanOf(List.of("docker-compose.yml"), List.of(),
                "services:\n  db:\n    image: postgres:15\n");

        DbSuggestion suggestion = detector.suggest(scan);

        assertThat(suggestion.likelihood()).isEqualTo(Likelihood.LIKELY);
    }

    @Test
    void composeMysqlImageSignalYieldsLikely() {
        RepoScan scan = scanOf(List.of("docker-compose.yml"), List.of(),
                "services:\n  db:\n    image: mysql:8\n");

        DbSuggestion suggestion = detector.suggest(scan);

        assertThat(suggestion.likelihood()).isEqualTo(Likelihood.LIKELY);
    }

    @Test
    void twoSignalsYieldCertain() {
        RepoScan scan = scanOf(List.of("prisma/schema.prisma", "migrations/001_init.sql"), List.of(), null);

        DbSuggestion suggestion = detector.suggest(scan);

        assertThat(suggestion.likelihood()).isEqualTo(Likelihood.CERTAIN);
        assertThat(suggestion.signals()).hasSize(2);
    }
}
