package dev.filipnikolov.vector.updater.dto;

public record UpdaterStatus(String service,
                            String phase,
                            String updateId,
                            String startedAt,
                            String targetImage,
                            String error) {
}
