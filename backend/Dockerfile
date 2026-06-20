# Etapa de build
FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# Etapa de runtime (imagen mínima, usuario sin privilegios)
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# JAVA_OPTS: la JVM se adapta al límite de memoria del contenedor (cgroup-aware).
# MaxRAMPercentage=75 deja ~25% para metaspace, threads y buffers fuera del heap.
# En Koyeb free (512MB) → ~384MB heap (suficiente para Spring Boot + MP SDK).
# Overrideable desde el orquestador (Koyeb/Render/docker-compose) si hace falta.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
