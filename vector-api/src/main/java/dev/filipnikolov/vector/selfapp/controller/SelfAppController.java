package dev.filipnikolov.vector.selfapp.controller;

import dev.filipnikolov.vector.selfapp.dto.PendingSelfUpdateDto;
import dev.filipnikolov.vector.selfapp.repository.PendingSelfUpdateRepository;
import dev.filipnikolov.vector.selfapp.service.SelfAppUpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/self-apps")
@RequiredArgsConstructor
public class SelfAppController {

    private final SelfAppUpdateService updateService;
    private final PendingSelfUpdateRepository pendingRepo;

    @PostMapping("/{appName}/update")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String appName) {
        updateService.triggerUpdate(appName);
        return ResponseEntity.accepted().body(Map.of("status", "triggered"));
    }

    @GetMapping("/{appName}/pending")
    public ResponseEntity<PendingSelfUpdateDto> pending(@PathVariable String appName) {
        return pendingRepo.findFirstByAppNameOrderByTriggeredAtDesc(appName)
                .map(PendingSelfUpdateDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
