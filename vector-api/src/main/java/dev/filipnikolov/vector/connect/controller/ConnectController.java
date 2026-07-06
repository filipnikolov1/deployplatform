package dev.filipnikolov.vector.connect.controller;

import dev.filipnikolov.vector.connect.dto.ConnectRequest;
import dev.filipnikolov.vector.connect.dto.ConnectResponse;
import dev.filipnikolov.vector.connect.dto.ScanResponse;
import dev.filipnikolov.vector.connect.service.AppNameTakenException;
import dev.filipnikolov.vector.connect.service.ConnectService;
import dev.filipnikolov.vector.connect.service.InstallationNotApprovedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/connect")
public class ConnectController {

    private final ConnectService connectService;

    public ConnectController(ConnectService connectService) {
        this.connectService = connectService;
    }

    @PostMapping("/scan")
    public ResponseEntity<?> scan(@RequestBody Map<String, String> body) {
        String repoFullName = body.get("repoFullName");
        ScanResponse response = connectService.scan(repoFullName);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> connect(@RequestBody ConnectRequest req) {
        try {
            ConnectResponse response = connectService.connect(req);
            return ResponseEntity.ok(response);
        } catch (InstallationNotApprovedException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (AppNameTakenException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
