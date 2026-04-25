package com.filipnikolov.launchpad.updater.dto;

public record UpdateResponse(String status, String updateId, String error) {
    public UpdateResponse(String status, String error) {
        this(status, null, error);
    }
}
