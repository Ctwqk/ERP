# Build the reactor first: MAVEN_OPTS=-Xmx512m ./mvnw -B verify
# A shared runtime image avoids five redundant Maven builds in the Docker daemon.
FROM eclipse-temurin:17-jre
ARG SERVICE
WORKDIR /app
COPY ${SERVICE}/target/${SERVICE}-0.0.1-SNAPSHOT.jar app.jar
USER 10001:10001
ENV SERVER_PORT=8080 JAVA_TOOL_OPTIONS="-Xmx512m -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
