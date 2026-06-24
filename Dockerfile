FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:resolve -B
COPY src ./src
RUN mvn package -B -DskipTests -Dmaven.test.skip=true

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S filesever && adduser -S filesever -G filesever
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /data/files /data/hostkeys && chown -R filesever:filesever /data /app
USER filesever
VOLUME ["/data/files", "/data/hostkeys"]
EXPOSE 8080 2222 21
ENTRYPOINT ["java", "-jar", "app.jar"]
