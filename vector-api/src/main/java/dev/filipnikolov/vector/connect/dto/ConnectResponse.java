package dev.filipnikolov.vector.connect.dto;

import dev.filipnikolov.vector.connect.workflow.WiringResult;

import java.util.List;

public record ConnectResponse(Long projectId, List<String> apps, WiringResult workflow) {}
