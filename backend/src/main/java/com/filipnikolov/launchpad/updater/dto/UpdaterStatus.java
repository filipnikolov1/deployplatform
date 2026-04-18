package com.filipnikolov.launchpad.updater.dto;

public record UpdaterStatus(String service,
                            String phase,
                            String updateId,
                            String startedAt,
                            String targetImage,
                            String error) {
}
