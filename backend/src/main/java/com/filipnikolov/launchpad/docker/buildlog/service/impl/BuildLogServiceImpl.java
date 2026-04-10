package com.filipnikolov.launchpad.docker.buildlog.service.impl;

import com.filipnikolov.launchpad.docker.buildlog.service.BuildLogService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class BuildLogServiceImpl implements BuildLogService {

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(String appName) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5 min timeout
        emitters.computeIfAbsent(appName, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(appName, emitter));
        emitter.onTimeout(() -> removeEmitter(appName, emitter));
        emitter.onError(e -> removeEmitter(appName, emitter));

        return emitter;
    }

    @Override
    public void send(String appName, String message) {
        List<SseEmitter> appEmitters = emitters.get(appName);
        if (appEmitters == null) return;

        for (SseEmitter emitter : appEmitters) {
            try {
                emitter.send(SseEmitter.event().data(message));
            } catch (Exception e) {
                removeEmitter(appName, emitter);
            }
        }
    }

    @Override
    public void complete(String appName) {
        List<SseEmitter> appEmitters = emitters.remove(appName);
        if (appEmitters == null) return;

        for (SseEmitter emitter : appEmitters) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
    }

    private void removeEmitter(String appName, SseEmitter emitter) {
        List<SseEmitter> appEmitters = emitters.get(appName);
        if (appEmitters != null) {
            appEmitters.remove(emitter);
        }
    }
}
