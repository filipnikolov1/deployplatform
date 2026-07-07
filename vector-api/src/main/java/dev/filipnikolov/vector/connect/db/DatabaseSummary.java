package dev.filipnikolov.vector.connect.db;

import java.time.LocalDateTime;

public record DatabaseSummary(String appName, String dbName, boolean orphaned, LocalDateTime createdAt) {
}
