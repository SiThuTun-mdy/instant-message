FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Dependencies first, so they are cached until pom.xml changes
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system app
USER app
COPY --from=build /app/target/*.jar /app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app.jar"]
