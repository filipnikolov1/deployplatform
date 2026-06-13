package dev.filipnikolov.vector.deployment.service.impl;

import dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest;
import dev.filipnikolov.vector.deployment.model.Deployment;
import dev.filipnikolov.vector.deployment.repository.DeploymentRepository;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.events.TriggerSource;
import dev.filipnikolov.vector.monitoring.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentTransactionHelperSubdomainTest {

    private DeploymentRepository deploymentRepository;
    private DeploymentTransactionHelper helper;

    @BeforeEach
    void setUp() {
        deploymentRepository = mock(DeploymentRepository.class);
        DeploymentEventService eventService = mock(DeploymentEventService.class);
        NotificationService notificationService = mock(NotificationService.class);
        helper = new DeploymentTransactionHelper(deploymentRepository, eventService, notificationService);
    }

    @Test
    void preExistingSubdomain_throws400InsteadOf500() {
        Deployment self = new Deployment();
        self.setId(1L);
        self.setAppName("my-app");

        Deployment other = new Deployment();
        other.setId(2L);
        other.setAppName("other-app");
        other.setSubdomain("taken");

        when(deploymentRepository.findByAppNameAndDeletedAtIsNull("my-app")).thenReturn(Optional.of(self));
        when(deploymentRepository.findBySubdomainAndDeletedAtIsNull("taken")).thenReturn(Optional.of(other));
        when(deploymentRepository.findByAppNameAndDeletedAtIsNull("taken")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> helper.saveSubdomainChange("my-app", "taken"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Subdomain already in use");
    }

    @Test
    void uniqueIndexRace_convertsDataIntegrityViolationTo400() {
        Deployment self = new Deployment();
        self.setId(1L);
        self.setAppName("my-app");

        when(deploymentRepository.findByAppNameAndDeletedAtIsNull("my-app")).thenReturn(Optional.of(self));
        when(deploymentRepository.findBySubdomainAndDeletedAtIsNull("racey")).thenReturn(Optional.empty());
        when(deploymentRepository.findByAppNameAndDeletedAtIsNull("racey")).thenReturn(Optional.empty());
        when(deploymentRepository.saveAndFlush(any(Deployment.class)))
                .thenThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> helper.saveSubdomainChange("my-app", "racey"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Subdomain already in use");
    }

    @Test
    void preCreateRejectsAppNameCollidingWithExistingSubdomain() {
        // repo.findByAppNameAndDeletedAtIsNull("shop") → empty (new app)
        // repo.findBySubdomainAndDeletedAtIsNull("shop") → existing other app
        Deployment other = new Deployment();
        other.setAppName("storefront");
        other.setSubdomain("shop");
        when(deploymentRepository.findByAppNameAndDeletedAtIsNull("shop")).thenReturn(Optional.empty());
        when(deploymentRepository.findBySubdomainAndDeletedAtIsNull("shop")).thenReturn(Optional.of(other));

        CreateDeploymentRequest req = new CreateDeploymentRequest(
                "shop", null, "repo/shop:latest", 3000, "main", null, null, null, null, null,
                TriggerSource.AUTOMATIC);

        assertThatThrownBy(() -> helper.preCreate(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subdomain");
    }
}
