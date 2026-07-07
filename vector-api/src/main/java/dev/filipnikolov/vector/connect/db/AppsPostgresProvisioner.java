package dev.filipnikolov.vector.connect.db;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import dev.filipnikolov.vector.envvar.crypto.EncryptionService;
import dev.filipnikolov.vector.envvar.service.EnvVarService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AppsPostgresProvisioner {

    private static final String CONTAINER_NAME = "vector-apps-postgres";
    private static final String VOLUME_NAME = "vector-apps-postgres-data";
    private static final String IMAGE = "postgres:16-alpine";
    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int PASSWORD_LENGTH = 32;
    private static final int MAX_IDENTIFIER_LENGTH = 60;

    private final DockerClient dockerClient;
    private final EnvVarService envVarService;
    private final EncryptionService encryptionService;
    private final ProvisionedDatabaseRepository provisionedDatabaseRepository;
    private final AppsPostgresConfigRepository appsPostgresConfigRepository;
    private final String network;
    private final SecureRandom random = new SecureRandom();

    public AppsPostgresProvisioner(DockerClient dockerClient,
                                    EnvVarService envVarService,
                                    EncryptionService encryptionService,
                                    ProvisionedDatabaseRepository provisionedDatabaseRepository,
                                    AppsPostgresConfigRepository appsPostgresConfigRepository,
                                    @Value("${traefik.network}") String network) {
        this.dockerClient = dockerClient;
        this.envVarService = envVarService;
        this.encryptionService = encryptionService;
        this.provisionedDatabaseRepository = provisionedDatabaseRepository;
        this.appsPostgresConfigRepository = appsPostgresConfigRepository;
        this.network = network;
    }

    @Transactional
    public ProvisionedDatabase provision(String appName) {
        var existing = provisionedDatabaseRepository.findByAppName(appName);
        if (existing.isPresent()) {
            ProvisionedDatabase row = existing.get();
            if (row.getOrphanedAt() == null) {
                return row;
            }
            row.setOrphanedAt(null);
            return provisionedDatabaseRepository.save(row);
        }

        String adminPassword = ensureAdminPassword();
        ensureContainer(adminPassword);

        String dbName = sanitizeIdentifier(appName);
        String dbUser = dbName;
        String dbPassword = generatePassword();

        execIfMissing("SELECT 1 FROM pg_roles WHERE rolname='" + dbUser + "'",
                "CREATE USER " + dbUser + " WITH PASSWORD '" + dbPassword + "'");
        execIfMissing("SELECT 1 FROM pg_database WHERE datname='" + dbName + "'",
                "CREATE DATABASE " + dbName + " OWNER " + dbUser);

        String databaseUrl = "postgresql://" + dbUser + ":" + dbPassword + "@" + CONTAINER_NAME + ":5432/" + dbName;
        envVarService.setEnvVar(appName, "DATABASE_URL", databaseUrl);

        ProvisionedDatabase row = new ProvisionedDatabase();
        row.setAppName(appName);
        row.setDbName(dbName);
        row.setDbUser(dbUser);
        row.setDbPassEnc(encryptionService.encrypt(dbPassword));
        row.setCreatedAt(LocalDateTime.now());
        return provisionedDatabaseRepository.save(row);
    }

    @Transactional
    public void orphan(String appName) {
        provisionedDatabaseRepository.findByAppName(appName).ifPresent(row -> {
            row.setOrphanedAt(LocalDateTime.now());
            provisionedDatabaseRepository.save(row);
        });
    }

    public List<DatabaseSummary> list() {
        return provisionedDatabaseRepository.findAll().stream()
                .map(row -> new DatabaseSummary(row.getAppName(), row.getDbName(),
                        row.getOrphanedAt() != null, row.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void drop(String dbName, String confirm) {
        if (!dbName.equals(confirm)) {
            throw new IllegalArgumentException("Confirmation does not match database name: " + dbName);
        }

        ProvisionedDatabase row = provisionedDatabaseRepository.findByDbName(dbName)
                .orElseThrow(() -> new IllegalArgumentException("Database not found: " + dbName));
        if (row.getOrphanedAt() == null) {
            throw new IllegalStateException("Database is not orphaned: " + dbName);
        }

        exec("DROP DATABASE " + row.getDbName());
        exec("DROP USER " + row.getDbUser());
        provisionedDatabaseRepository.delete(row);
    }

    private String ensureAdminPassword() {
        List<AppsPostgresConfig> rows = appsPostgresConfigRepository.findAll();
        if (!rows.isEmpty()) {
            return encryptionService.decrypt(rows.get(0).getAdminPasswordEnc());
        }
        String password = generatePassword();
        AppsPostgresConfig config = new AppsPostgresConfig();
        config.setAdminPasswordEnc(encryptionService.encrypt(password));
        config.setCreatedAt(LocalDateTime.now());
        appsPostgresConfigRepository.save(config);
        return password;
    }

    private void ensureContainer(String adminPassword) {
        if (isRunning()) {
            return;
        }

        dockerClient.createVolumeCmd().withName(VOLUME_NAME).exec();

        CreateContainerResponse response = dockerClient.createContainerCmd(IMAGE)
                .withName(CONTAINER_NAME)
                .withEnv(List.of("POSTGRES_PASSWORD=" + adminPassword))
                .withHostConfig(HostConfig.newHostConfig()
                        .withNetworkMode(network)
                        .withBinds(com.github.dockerjava.api.model.Bind.parse(
                                VOLUME_NAME + ":/var/lib/postgresql/data")))
                .exec();

        dockerClient.startContainerCmd(response.getId()).exec();
    }

    private boolean isRunning() {
        try {
            return Boolean.TRUE.equals(
                    dockerClient.inspectContainerCmd(CONTAINER_NAME).exec().getState().getRunning());
        } catch (Exception e) {
            return false;
        }
    }

    private void execIfMissing(String existsQuery, String createStatement) {
        String output = exec(existsQuery);
        if (output.contains("1")) {
            return;
        }
        exec(createStatement);
    }

    private String exec(String psqlCommand) {
        String execId = dockerClient.execCreateCmd(CONTAINER_NAME)
                .withCmd("psql", "-U", "postgres", "-c", psqlCommand)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec()
                .getId();

        List<Frame> frames = new ArrayList<>();
        try {
            dockerClient.execStartCmd(execId).exec(new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame frame) {
                    frames.add(frame);
                }
            }).awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for psql exec to complete", e);
        }

        Integer exitCode = dockerClient.inspectExecCmd(execId).exec().getExitCode();
        String output = frames.stream()
                .map(frame -> new String(frame.getPayload(), StandardCharsets.UTF_8))
                .reduce("", String::concat);

        if (exitCode == null || exitCode != 0) {
            throw new RuntimeException("psql exec failed with exit code " + exitCode + ": " + psqlCommand);
        }

        return output;
    }

    private String sanitizeIdentifier(String appName) {
        String lowered = appName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        String base = "app_" + lowered;
        if (base.length() > MAX_IDENTIFIER_LENGTH) {
            base = base.substring(0, MAX_IDENTIFIER_LENGTH);
        }

        String candidate = base;
        int suffix = 2;
        while (provisionedDatabaseRepository.findByDbName(candidate).isPresent()) {
            String suffixStr = "_" + suffix;
            int trimTo = Math.min(base.length(), MAX_IDENTIFIER_LENGTH - suffixStr.length());
            candidate = base.substring(0, trimTo) + suffixStr;
            suffix++;
        }
        return candidate;
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }
}
