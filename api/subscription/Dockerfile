# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -Dmaven.wagon.http.retryHandler.count=5 dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -Dmaven.wagon.http.retryHandler.count=5 package -DskipTests
RUN java -Djarmode=layertools -jar target/*.jar extract --destination extracted

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S nexo && adduser -S nexo -G nexo \
    && chown -R nexo:nexo /app

COPY --from=build --chown=nexo:nexo /workspace/extracted/dependencies/ ./
COPY --from=build --chown=nexo:nexo /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=nexo:nexo /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=nexo:nexo /workspace/extracted/application/ ./

USER nexo
EXPOSE 8093

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8093/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
