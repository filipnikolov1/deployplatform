package com.filipnikolov.launchpad.deployment.controller;

import com.filipnikolov.launchpad.deployment.dto.DeploymentEventDto;
import com.filipnikolov.launchpad.deployment.service.DeploymentEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DeploymentEventController {

    private final DeploymentEventService eventService;

    @GetMapping("/api/apps/{name}/events")
    public List<DeploymentEventDto> listForApp(@PathVariable String name,
                                               @RequestParam(defaultValue = "20") int limit) {
        return eventService.listForApp(name, limit).stream()
                .map(DeploymentEventDto::from)
                .toList();
    }

    @GetMapping("/api/events")
    public List<DeploymentEventDto> listGlobal(@RequestParam(defaultValue = "50") int limit) {
        return eventService.listGlobal(limit).stream()
                .map(DeploymentEventDto::from)
                .toList();
    }

    @GetMapping("/api/apps/{name}/events/latest")
    public ResponseEntity<DeploymentEventDto> latest(@PathVariable String name) {
        return eventService.latestForApp(name)
                .map(DeploymentEventDto::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
