package com.filipnikolov.launchpad.deployment.service.impl;

import com.filipnikolov.launchpad.deployment.dto.CreateDeploymentRequest;
import com.filipnikolov.launchpad.deployment.model.DeploymentEvent;
import com.filipnikolov.launchpad.deployment.model.DeploymentEventStatus;
import com.filipnikolov.launchpad.deployment.model.DeploymentEventType;
import com.filipnikolov.launchpad.deployment.repository.DeploymentEventRepository;
import com.filipnikolov.launchpad.deployment.service.DeploymentEventService;
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
        DeploymentEvent e = new DeploymentEvent();
        e.setAppName(appName);
        e.setEventType(type);
        e.setStatus(status);
        e.setDurationMs(durationMs);
        e.setErrorMessage(errorMessage);
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
