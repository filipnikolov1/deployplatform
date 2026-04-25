# Stage 1: Build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# Stage 2: Run
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8082
ARG VECTOR_GIT_SHA=dev
ARG VECTOR_GIT_MESSAGE=local
ENV VECTOR_GIT_SHA=${VECTOR_GIT_SHA}
ENV VECTOR_GIT_MESSAGE=${VECTOR_GIT_MESSAGE}
ENTRYPOINT ["java", "-jar", "app.jar"]
