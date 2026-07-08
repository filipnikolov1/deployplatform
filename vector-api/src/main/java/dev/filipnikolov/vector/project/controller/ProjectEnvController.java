package dev.filipnikolov.vector.project.controller;

import dev.filipnikolov.vector.project.service.ProjectEnvService;
import dev.filipnikolov.vector.project.service.ProjectEnvVarView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{id}/env")
public class ProjectEnvController {

    private final ProjectEnvService projectEnvService;

    public ProjectEnvController(ProjectEnvService projectEnvService) {
        this.projectEnvService = projectEnvService;
    }

    @GetMapping
    public ResponseEntity<List<ProjectEnvVarView>> list(@PathVariable Long id) {
        return ResponseEntity.ok(projectEnvService.listProjectEnvVars(id));
    }

    @PutMapping
    public ResponseEntity<Void> upsert(@PathVariable Long id, @RequestBody Map<String, String> body) {
        projectEnvService.setProjectEnvVar(id, body.get("key"), body.get("value"));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @PathVariable String key) {
        projectEnvService.deleteProjectEnvVar(id, key);
        return ResponseEntity.noContent().build();
    }
}
