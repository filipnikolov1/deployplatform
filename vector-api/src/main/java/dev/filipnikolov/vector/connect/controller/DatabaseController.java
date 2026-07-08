package dev.filipnikolov.vector.connect.controller;

import dev.filipnikolov.vector.connect.db.AppsPostgresProvisioner;
import dev.filipnikolov.vector.connect.db.DatabaseSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/databases")
public class DatabaseController {

    private final AppsPostgresProvisioner provisioner;

    public DatabaseController(AppsPostgresProvisioner provisioner) {
        this.provisioner = provisioner;
    }

    @GetMapping
    public ResponseEntity<List<DatabaseSummary>> list() {
        return ResponseEntity.ok(provisioner.list());
    }

    @DeleteMapping("/{dbName}")
    public ResponseEntity<?> delete(@PathVariable String dbName, @RequestParam String confirm) {
        if (!dbName.equals(confirm)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Confirmation does not match database name"));
        }
        try {
            provisioner.drop(dbName, confirm);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
