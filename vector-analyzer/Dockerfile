# Build command: docker build -f vector-analyzer/Dockerfile .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY vector-events/ vector-events/
COPY vector-ai/ vector-ai/
COPY vector-github/ vector-github/
COPY vector-api/ vector-api/
COPY vector-analyzer/ vector-analyzer/
RUN mvn -pl vector-analyzer -am clean package -DskipTests -q

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/vector-analyzer/target/vector-analyzer-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
