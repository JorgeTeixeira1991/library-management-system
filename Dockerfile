FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --uid 10001 library
COPY --from=build /workspace/target/library-system-1.0.0.jar /app/app.jar
USER library
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
