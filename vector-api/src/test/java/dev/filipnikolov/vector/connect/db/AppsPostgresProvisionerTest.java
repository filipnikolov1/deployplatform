package dev.filipnikolov.vector.connect.db;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.CreateVolumeCmd;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse.ContainerState;
import com.github.dockerjava.api.command.InspectExecCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import dev.filipnikolov.vector.envvar.crypto.EncryptionService;
import dev.filipnikolov.vector.envvar.service.EnvVarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppsPostgresProvisionerTest {

    private DockerClient dockerClient;
    private EnvVarService envVarService;
    private EncryptionService encryptionService;
    private ProvisionedDatabaseRepository provisionedDatabaseRepository;
    private AppsPostgresConfigRepository appsPostgresConfigRepository;
    private AppsPostgresProvisioner provisioner;

    @BeforeEach
    void setUp() {
        dockerClient = mock(DockerClient.class);
        envVarService = mock(EnvVarService.class);
        encryptionService = mock(EncryptionService.class);
        provisionedDatabaseRepository = mock(ProvisionedDatabaseRepository.class);
        appsPostgresConfigRepository = mock(AppsPostgresConfigRepository.class);

        when(encryptionService.encrypt(anyString())).thenAnswer(inv -> "enc(" + inv.getArgument(0) + ")");
        when(encryptionService.decrypt(anyString())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s.substring(4, s.length() - 1);
        });
        when(appsPostgresConfigRepository.findAll()).thenReturn(List.of());
        when(appsPostgresConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(provisionedDatabaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(provisionedDatabaseRepository.findByDbNameStartingWith(anyString())).thenReturn(List.of());

        // Container doesn't exist yet by default.
        InspectContainerCmd inspectCmd = mock(InspectContainerCmd.class);
        when(dockerClient.inspectContainerCmd("vector-apps-postgres")).thenReturn(inspectCmd);
        when(inspectCmd.exec()).thenThrow(new NotFoundException("not found"));

        CreateVolumeCmd createVolumeCmd = mock(CreateVolumeCmd.class);
        when(dockerClient.createVolumeCmd()).thenReturn(createVolumeCmd);
        when(createVolumeCmd.withName(anyString())).thenReturn(createVolumeCmd);

        CreateContainerCmd createContainerCmd = mock(CreateContainerCmd.class, org.mockito.Answers.RETURNS_SELF);
        when(dockerClient.createContainerCmd(anyString())).thenReturn(createContainerCmd);
        CreateContainerResponse createResponse = mock(CreateContainerResponse.class);
        when(createResponse.getId()).thenReturn("container-id");
        when(createContainerCmd.exec()).thenReturn(createResponse);

        StartContainerCmd startContainerCmd = mock(StartContainerCmd.class);
        when(dockerClient.startContainerCmd(anyString())).thenReturn(startContainerCmd);

        mockExec("CREATE USER exists check", 1);

        provisioner = new AppsPostgresProvisioner(dockerClient, envVarService, encryptionService,
                provisionedDatabaseRepository, appsPostgresConfigRepository, "traefik");
    }

    @SuppressWarnings("unchecked")
    private void mockExec(String label, int callIndex) {
        ExecCreateCmd execCreateCmd = mock(ExecCreateCmd.class, org.mockito.Answers.RETURNS_SELF);
        ExecCreateCmdResponse execCreateResponse = mock(ExecCreateCmdResponse.class);
        when(execCreateResponse.getId()).thenReturn("exec-" + label);
        when(execCreateCmd.exec()).thenReturn(execCreateResponse);
        when(dockerClient.execCreateCmd(anyString())).thenReturn(execCreateCmd);

        ExecStartCmd execStartCmd = mock(ExecStartCmd.class, org.mockito.Answers.RETURNS_SELF);
        when(dockerClient.execStartCmd(anyString())).thenReturn(execStartCmd);
        when(execStartCmd.exec(any())).thenAnswer(inv -> {
            ResultCallback.Adapter<Frame> callback = inv.getArgument(0);
            callback.onComplete();
            return callback;
        });

        InspectExecCmd inspectExecCmd = mock(InspectExecCmd.class);
        InspectExecResponse inspectExecResponse = mock(InspectExecResponse.class);
        when(inspectExecResponse.getExitCode()).thenReturn(0);
        when(inspectExecCmd.exec()).thenReturn(inspectExecResponse);
        when(dockerClient.inspectExecCmd(anyString())).thenReturn(inspectExecCmd);
    }

    @Test
    void ensuresContainerCreatedOnlyOnceWhenAlreadyRunning() throws Exception {
        InspectContainerCmd inspectCmd = mock(InspectContainerCmd.class);
        InspectContainerResponse inspectResponse = mock(InspectContainerResponse.class);
        ContainerState state = mock(ContainerState.class);
        when(state.getRunning()).thenReturn(true);
        when(inspectResponse.getState()).thenReturn(state);
        when(inspectCmd.exec()).thenReturn(inspectResponse);
        when(dockerClient.inspectContainerCmd("vector-apps-postgres")).thenReturn(inspectCmd);

        provisioner.provision("shop-api");

        verify(dockerClient, never()).createContainerCmd(anyString());
    }

    @Test
    void createsContainerWhenMissing() {
        provisioner.provision("shop-api");

        verify(dockerClient).createContainerCmd("postgres:16-alpine");
    }

    @Test
    void createsVolumeBeforeCreatingContainer() {
        provisioner.provision("shop-api");

        var createVolumeCmd = dockerClient.createVolumeCmd();
        verify(createVolumeCmd).withName("vector-apps-postgres-data");
        verify(createVolumeCmd).exec();
    }

    @Test
    void provisionIsIdempotentForAlreadyProvisionedApp() {
        ProvisionedDatabase existing = new ProvisionedDatabase();
        existing.setAppName("shop-api");
        existing.setDbName("app_shop_api");
        existing.setDbUser("app_shop_api");
        existing.setDbPassEnc("enc(existing-password)");
        existing.setCreatedAt(java.time.LocalDateTime.now());
        when(provisionedDatabaseRepository.findByAppName("shop-api")).thenReturn(Optional.of(existing));

        ProvisionedDatabase result = provisioner.provision("shop-api");

        assertThat(result).isSameAs(existing);
        verify(dockerClient, never()).execCreateCmd(anyString());
        verify(provisionedDatabaseRepository, never()).save(any());
        verify(envVarService, never()).setEnvVar(anyString(), anyString(), anyString());
    }

    @Test
    void provisionUnorphansAndReusesExistingRowWhenPreviouslyOrphaned() {
        ProvisionedDatabase existing = new ProvisionedDatabase();
        existing.setAppName("shop-api");
        existing.setDbName("app_shop_api");
        existing.setDbUser("app_shop_api");
        existing.setDbPassEnc("enc(existing-password)");
        existing.setCreatedAt(java.time.LocalDateTime.now());
        existing.setOrphanedAt(java.time.LocalDateTime.now());
        when(provisionedDatabaseRepository.findByAppName("shop-api")).thenReturn(Optional.of(existing));

        ProvisionedDatabase result = provisioner.provision("shop-api");

        assertThat(result).isSameAs(existing);
        assertThat(result.getOrphanedAt()).isNull();
        verify(dockerClient, never()).execCreateCmd(anyString());
        verify(provisionedDatabaseRepository).save(existing);
    }

    @Test
    void checksForExistingRoleAndDatabaseBeforeCreatingThem() {
        provisioner.provision("shop-api");

        var cmdCaptor = org.mockito.ArgumentCaptor.forClass(String[].class);
        verify(dockerClient.execCreateCmd("container-id"), org.mockito.Mockito.atLeastOnce())
                .withCmd(cmdCaptor.capture());

        List<String> statements = cmdCaptor.getAllValues().stream()
                .map(cmd -> cmd[cmd.length - 1])
                .toList();

        assertThat(statements).anyMatch(s -> s.contains("SELECT 1 FROM pg_roles"));
        assertThat(statements).anyMatch(s -> s.contains("SELECT 1 FROM pg_database"));
        assertThat(statements).anyMatch(s -> s.startsWith("CREATE USER"));
        assertThat(statements).anyMatch(s -> s.startsWith("CREATE DATABASE"));
    }

    @Test
    void execThrowsWhenPsqlStatementExitsNonZero() {
        InspectExecCmd inspectExecCmd = mock(InspectExecCmd.class);
        InspectExecResponse inspectExecResponse = mock(InspectExecResponse.class);
        when(inspectExecResponse.getExitCode()).thenReturn(1);
        when(inspectExecCmd.exec()).thenReturn(inspectExecResponse);
        when(dockerClient.inspectExecCmd(anyString())).thenReturn(inspectExecCmd);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> provisioner.provision("shop-api"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void sanitizesAppNameToDbNameAndUser() {
        provisioner.provision("Shop API!!");

        var captor = org.mockito.ArgumentCaptor.forClass(ProvisionedDatabase.class);
        verify(provisionedDatabaseRepository).save(captor.capture());
        ProvisionedDatabase saved = captor.getValue();

        assertThat(saved.getDbName()).matches("app_[a-z0-9_]+");
        assertThat(saved.getDbUser()).isEqualTo(saved.getDbName());
        assertThat(saved.getDbName()).doesNotContainPattern("[^a-z0-9_]");
    }

    @Test
    void capsSanitizedNameAt60CharsAndAppendsSuffixOnCollision() {
        String longName = "a".repeat(100);
        String expectedBase = ("app_" + longName);
        String cappedBase = expectedBase.length() > 60 ? expectedBase.substring(0, 60) : expectedBase;

        ProvisionedDatabase existing = new ProvisionedDatabase();
        existing.setDbName(cappedBase);
        when(provisionedDatabaseRepository.findByDbName(cappedBase)).thenReturn(Optional.of(existing));
        when(provisionedDatabaseRepository.findByDbName(org.mockito.ArgumentMatchers.argThat(
                name -> name != null && !name.equals(cappedBase)))).thenReturn(Optional.empty());

        provisioner.provision(longName);

        var captor = org.mockito.ArgumentCaptor.forClass(ProvisionedDatabase.class);
        verify(provisionedDatabaseRepository).save(captor.capture());
        ProvisionedDatabase saved = captor.getValue();

        assertThat(saved.getDbName()).hasSizeLessThanOrEqualTo(60);
        assertThat(saved.getDbName()).endsWith("_2");
        assertThat(saved.getDbName()).isNotEqualTo(cappedBase);
    }

    @Test
    void generatesUrlSafe32CharPassword() {
        provisioner.provision("shop-api");

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(encryptionService, times(2)).encrypt(captor.capture());
        // First encrypt call is the admin password (config bootstrap), second is the app db password.
        String appPassword = captor.getAllValues().get(1);
        assertThat(appPassword).hasSize(32);
        assertThat(appPassword).matches(Pattern.compile("^[A-Za-z0-9]{32}$"));
    }

    @Test
    void issuesCreateUserThenCreateDatabaseExecStatements() {
        provisioner.provision("shop-api");

        var cmdCaptor = org.mockito.ArgumentCaptor.forClass(String[].class);
        verify(dockerClient.execCreateCmd("container-id"), times(4)).withCmd(cmdCaptor.capture());

        List<String> statements = cmdCaptor.getAllValues().stream()
                .map(cmd -> cmd[cmd.length - 1])
                .toList();
        int createUserIndex = indexOfStatementStartingWith(statements, "CREATE USER");
        int createDatabaseIndex = indexOfStatementStartingWith(statements, "CREATE DATABASE");

        assertThat(createUserIndex).isLessThan(createDatabaseIndex);
    }

    private static int indexOfStatementStartingWith(List<String> statements, String prefix) {
        for (int i = 0; i < statements.size(); i++) {
            if (statements.get(i).startsWith(prefix)) {
                return i;
            }
        }
        throw new AssertionError("No statement starting with " + prefix);
    }

    @Test
    void upsertsDatabaseUrlEnvVar() {
        provisioner.provision("shop-api");

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(envVarService).setEnvVar(eq("shop-api"), eq("DATABASE_URL"), captor.capture());
        assertThat(captor.getValue()).startsWith("postgresql://");
        assertThat(captor.getValue()).contains("@vector-apps-postgres:5432/");
    }

    @Test
    void insertsProvisionedDatabaseRow() {
        provisioner.provision("shop-api");

        verify(provisionedDatabaseRepository).save(any(ProvisionedDatabase.class));
    }

    @Test
    void orphanSetsOrphanedAtInsteadOfDropping() {
        ProvisionedDatabase row = new ProvisionedDatabase();
        row.setAppName("shop-api");
        row.setDbName("app_shop_api");
        row.setDbUser("app_shop_api");
        when(provisionedDatabaseRepository.findByAppName("shop-api")).thenReturn(Optional.of(row));

        provisioner.orphan("shop-api");

        assertThat(row.getOrphanedAt()).isNotNull();
        verify(provisionedDatabaseRepository).save(row);
        verify(dockerClient, never()).execCreateCmd(anyString());
    }

    @Test
    void dropRejectsWhenNotOrphaned() {
        ProvisionedDatabase row = new ProvisionedDatabase();
        row.setDbName("app_shop_api");
        row.setDbUser("app_shop_api");
        when(provisionedDatabaseRepository.findByDbName("app_shop_api")).thenReturn(Optional.of(row));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> provisioner.drop("app_shop_api", "app_shop_api"))
                .isInstanceOf(IllegalStateException.class);

        verify(provisionedDatabaseRepository, never()).delete(any());
    }

    @Test
    void dropRejectsMismatchedConfirm() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> provisioner.drop("app_shop_api", "wrong"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(provisionedDatabaseRepository, never()).findByDbName(anyString());
    }

    @Test
    void dropExecutesOnOrphanedDatabase() {
        ProvisionedDatabase row = new ProvisionedDatabase();
        row.setDbName("app_shop_api");
        row.setDbUser("app_shop_api");
        row.setOrphanedAt(java.time.LocalDateTime.now());
        when(provisionedDatabaseRepository.findByDbName("app_shop_api")).thenReturn(Optional.of(row));

        provisioner.drop("app_shop_api", "app_shop_api");

        verify(provisionedDatabaseRepository).delete(row);
        var cmdCaptor = org.mockito.ArgumentCaptor.forClass(String[].class);
        verify(dockerClient.execCreateCmd(anyString()), times(2)).withCmd(cmdCaptor.capture());
    }

    @Test
    void listMapsOrphanedFlag() {
        ProvisionedDatabase active = new ProvisionedDatabase();
        active.setAppName("a");
        active.setDbName("app_a");
        active.setCreatedAt(java.time.LocalDateTime.now());
        ProvisionedDatabase orphaned = new ProvisionedDatabase();
        orphaned.setAppName("b");
        orphaned.setDbName("app_b");
        orphaned.setCreatedAt(java.time.LocalDateTime.now());
        orphaned.setOrphanedAt(java.time.LocalDateTime.now());
        when(provisionedDatabaseRepository.findAll()).thenReturn(List.of(active, orphaned));

        List<DatabaseSummary> result = provisioner.list();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).orphaned()).isFalse();
        assertThat(result.get(1).orphaned()).isTrue();
    }
}
