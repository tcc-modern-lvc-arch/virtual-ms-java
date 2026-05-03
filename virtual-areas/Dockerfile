# Build context: TCC root (docker-compose sets context: ..)
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

# 1. Install proto-shared stubs into local Maven repo
COPY proto-shared/ ./proto-shared/
RUN mvn -f proto-shared/pom.xml install -DskipTests -q

# 2. Build the application
COPY virtual-ms-java/ ./virtual-ms-java/
RUN mvn -f virtual-ms-java/virtual-areas/pom.xml package -DskipTests -q

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /workspace/virtual-ms-java/virtual-areas/target/virtual-areas.jar app.jar
EXPOSE 8082 50052
ENTRYPOINT ["java", "-jar", "app.jar"]
