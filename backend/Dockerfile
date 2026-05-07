FROM maven:3.9.8-eclipse-temurin-21 AS builder
WORKDIR /workspace
COPY backend/ ./backend/
RUN mvn -f backend/pom.xml -pl api -am clean install -DskipTests \
    && mvn -f backend/api/pom.xml -DskipTests package spring-boot:repackage

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /workspace/backend/api/target/api-0.1.0-SNAPSHOT.jar /app/api.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/api.jar"]
