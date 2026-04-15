package com.filipnikolov.launchpad.selfapp.controller;

import com.filipnikolov.launchpad.selfapp.service.SelfAppUpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/{appName}/update")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String appName) {
        updateService.triggerUpdate(appName);
        return ResponseEntity.accepted().body(Map.of("status", "triggered"));
    }
}
