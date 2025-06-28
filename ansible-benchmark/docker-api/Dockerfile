FROM openjdk:21-jdk-slim

ARG JAR_FILE=target/api-http-1.0-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8086

ENTRYPOINT ["java", "-jar", "/app.jar"]
