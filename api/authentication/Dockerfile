FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests
RUN java -Djarmode=layertools -jar target/*.jar extract --destination extracted

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S sophia && adduser -S sophia -G sophia \
    && chown -R sophia:sophia /app

COPY --from=build --chown=sophia:sophia /workspace/extracted/dependencies/ ./
COPY --from=build --chown=sophia:sophia /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=sophia:sophia /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=sophia:sophia /workspace/extracted/application/ ./

USER sophia
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
