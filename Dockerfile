FROM openjdk:22
ARG JAR_FILE=target/*.jar
WORKDIR /app
COPY ./target/batch-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar","app.jar"]