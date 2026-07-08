package dev.filipnikolov.vector.github.client.dto;

public record RunJob(long id, String name, String status, String conclusion, String htmlUrl) {}
