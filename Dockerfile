FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY vectis-core ./vectis-core
COPY vectis-autoconfigure ./vectis-autoconfigure
COPY vectis-spring-boot-starter ./vectis-spring-boot-starter
COPY vectis-sample-app ./vectis-sample-app
RUN mvn -B -ntp verify

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd --system vectis && useradd --system --gid vectis vectis
COPY --from=build --chown=vectis:vectis /workspace/vectis-sample-app/target/vectis-sample-app-*.jar /app/vectis.jar
USER vectis
ENV SPRING_PROFILES_ACTIVE=demo
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/vectis.jar"]
