package dev.filipnikolov.vector.envvar.controller;

import dev.filipnikolov.vector.envvar.service.EnvVarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/apps/{appName}/env")
@RequiredArgsConstructor
public class EnvVarController {

    private final EnvVarService envVarService;

    @GetMapping
    public ResponseEntity<Map<String, String>> getEnvVars(@PathVariable String appName) {
        return ResponseEntity.ok(envVarService.getEnvVars(appName));
    }

    @PutMapping("/{key}")
    public ResponseEntity<Void> setEnvVar(
            @PathVariable String appName,
            @PathVariable String key,
            @RequestBody String value) {
        envVarService.setEnvVar(appName, key, value);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> deleteEnvVar(
            @PathVariable String appName,
            @PathVariable String key) {
        envVarService.deleteEnvVar(appName, key);
        return ResponseEntity.noContent().build();
    }
}
