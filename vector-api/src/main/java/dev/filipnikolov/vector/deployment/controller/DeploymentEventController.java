package dev.filipnikolov.vector.deployment.controller;

import dev.filipnikolov.vector.deployment.dto.DeploymentEventMapper;
import dev.filipnikolov.vector.deployment.service.DeploymentEventService;
import dev.filipnikolov.vector.docker.service.DockerService;
import dev.filipnikolov.vector.events.dto.DeploymentEventDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class DeploymentEventController {

    private final DeploymentEventService eventService;
    private final DockerService dockerService;

    @GetMapping("/api/apps/{name}/events")
    public List<DeploymentEventDto> listForApp(@PathVariable String name,
                                               @RequestParam(defaultValue = "20") int limit) {
        Map<String, Boolean> cache = new HashMap<>();
        return eventService.listForApp(name, limit).stream()
                .map(e -> DeploymentEventMapper.from(e, availabilityFor(e.getImageName(), cache)))
                .toList();
    }

    @GetMapping("/api/events")
    public List<DeploymentEventDto> listGlobal(@RequestParam(defaultValue = "50") int limit) {
        return eventService.listGlobal(limit).stream()
                .map(DeploymentEventMapper::from)
                .toList();
    }

    @GetMapping("/api/apps/{name}/events/latest")
    public ResponseEntity<DeploymentEventDto> latest(@PathVariable String name) {
        return eventService.latestForApp(name)
                .map(DeploymentEventMapper::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private Boolean availabilityFor(String imageName, Map<String, Boolean> cache) {
        if (imageName == null || imageName.isBlank()) return null;
        return cache.computeIfAbsent(imageName, dockerService::imageExistsLocally);
    }
}
