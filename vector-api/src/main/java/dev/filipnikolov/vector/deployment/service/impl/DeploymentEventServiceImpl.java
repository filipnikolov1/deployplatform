package dev.filipnikolov.vector.deployment.service.impl;

import dev.filipnikolov.vector.deployment.dto.CreateDeploymentRequest;
import dev.filipnikolov.vector.deployment.model.DeploymentEvent;
import dev.filipnikolov.vector.events.DeploymentEventStatus;
import dev.filipnikolov.vector.events.DeploymentEventType;
import dev.filipnikolov.vector.deployment.repository.DeploymentEventRepository;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DeploymentEventServiceImpl implements DeploymentEventService {

    private final DeploymentEventRepository repo;

    @Override
    public DeploymentEvent record(DeploymentEventType type,
                                  DeploymentEventStatus status,
                                  String appName,
                                  CreateDeploymentRequest ctx,
                                  Long durationMs,
                                  String errorMessage) {
        return record(type, status, appName, ctx, durationMs, errorMessage, null);
    }

    @Override
    public DeploymentEvent record(DeploymentEventType type,
                                  DeploymentEventStatus status,
                                  String appName,
                                  CreateDeploymentRequest ctx,
                                  Long durationMs,
                                  String errorMessage,
                                  String operationId) {
        DeploymentEvent e = new DeploymentEvent();
        e.setAppName(appName);
        e.setEventType(type);
        e.setStatus(status);
        e.setDurationMs(durationMs);
        e.setErrorMessage(errorMessage);
        e.setOperationId(operationId);
        e.setCreatedAt(LocalDateTime.now());
        if (status != DeploymentEventStatus.IN_PROGRESS) {
            e.setFinishedAt(LocalDateTime.now());
        }
        if (ctx != null) {
            e.setImageName(ctx.imageName());
            e.setBranch(ctx.branch());
            e.setCommitSha(ctx.commitSha());
            e.setCommitMessage(ctx.commitMessage());
            e.setCommitAuthor(ctx.commitAuthor());
            e.setTriggeredBy(ctx.triggeredBy());
        }
        return repo.save(e);
    }

    @Override
    public List<DeploymentEvent> listForApp(String appName, int limit) {
        int clamped = clampLimit(limit, 20);
        return repo.findByAppNameOrderByCreatedAtDesc(appName, PageRequest.of(0, clamped));
    }

    @Override
    public List<DeploymentEvent> listGlobal(int limit) {
        int clamped = clampLimit(limit, 50);
        return repo.findAllByOrderByCreatedAtDesc(PageRequest.of(0, clamped));
    }

    @Override
    public Optional<DeploymentEvent> latestForApp(String appName) {
        return repo.findTopByAppNameAndEventTypeOrderByCreatedAtDesc(
                appName, DeploymentEventType.DEPLOY_FINISHED);
    }

    private static int clampLimit(int limit, int defaultIfNonPositive) {
        if (limit <= 0) return defaultIfNonPositive;
        if (limit > 200) return 200;
        return limit;
    }
}
