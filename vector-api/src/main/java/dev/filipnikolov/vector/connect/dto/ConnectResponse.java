package dev.filipnikolov.vector.connect.dto;

import dev.filipnikolov.vector.connect.workflow.WiringResult;

import java.util.List;
import java.util.Map;

public record ConnectResponse(Long projectId, List<String> apps, WiringResult workflow,
                               Map<String, List<String>> injectedEnv) {}
