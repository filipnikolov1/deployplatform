# Spring Boot

Framework powering [[Launchpad]]'s backend.

## Stack

- **Spring Boot** with Spring Web, Spring Security, Spring Data JPA
- **Java 21** (Eclipse Temurin)
- **Maven** build system (Maven Wrapper included)
- **Lombok** for boilerplate reduction
- **Flyway** for database migrations
- **docker-java** for Docker API interaction
- **Jackson** for JSON parsing

## Key Configuration

`application.properties` — see [[Configuration]].

Server runs on port **8082**.

## Notable Spring Features Used

- `@Scheduled` — [[Uptime Monitoring]] health check loop
- `SseEmitter` — [[Build Log Streaming]] real-time events
- `SecurityFilterChain` — [[Security]] setup
- `FilterRegistrationBean` — Rate limiting filter
- Spring Data JPA repositories — [[Database Schema]] access

See also: [[Architecture Overview]], [[Configuration]]

#architecture
