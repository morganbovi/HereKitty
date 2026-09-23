# Multi-stage: build the fat jar with Gradle, ship it on a slim JRE. Same image runs identically
# whether it's deployed to a platform (Fly.io, Railway, Render, ...) or `docker run` at home.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew --version

COPY src ./src
RUN ./gradlew shadowJar --no-daemon --console=plain

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/build/libs/HereKittyRelay-all.jar app.jar

EXPOSE 7050
# RELAY_JWT_SECRET is required: it verifies the short-lived, session-scoped credentials minted
# by Firebase. PORT defaults to 7050 if unset.
ENTRYPOINT ["java", "-jar", "app.jar"]
