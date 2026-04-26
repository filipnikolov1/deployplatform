# Build command: docker build -f vector-api/Dockerfile .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY vector-events/ vector-events/
COPY vector-ai/ vector-ai/
COPY vector-api/ vector-api/
RUN mvn -pl vector-api -am clean package -DskipTests -q

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/vector-api/target/vector-api-*.jar app.jar
EXPOSE 8082
ARG VECTOR_GIT_SHA=
ARG VECTOR_GIT_MESSAGE=
ARG VECTOR_GIT_BRANCH=
ENV VECTOR_GIT_SHA=${VECTOR_GIT_SHA}
ENV VECTOR_GIT_MESSAGE=${VECTOR_GIT_MESSAGE}
ENV VECTOR_GIT_BRANCH=${VECTOR_GIT_BRANCH}
ENTRYPOINT ["java", "-jar", "app.jar"]
