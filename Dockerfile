# Mentor Matching (Subsystem 4) — the whole subsystem as one container.
# Builds and runs with nothing but `docker build` and `docker run`.
#
# Stages:
#   test   `docker build --target test .` compiles and runs the JUnit tests with
#          Gradle inside the container. CI runs it on every PR. A plain
#          `docker build` skips it, so the image still builds without Gradle.
#   build  compiles the service with javac.
#   (last) the runtime image.

# ---- Test stage ----
# Full JDK on Ubuntu rather than Alpine: Gradle's native helpers expect glibc.
FROM eclipse-temurin:21-jdk AS test

WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
# A Windows checkout can leave CRLF line endings in gradlew, which breaks it here.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

# Download Gradle and the test dependencies in their own layer, so changing a
# source file does not download them again.
RUN ./gradlew --no-daemon dependencies > /dev/null

COPY backend ./backend
RUN ./gradlew --no-daemon build

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Java sources live under backend/src in this repo.
COPY backend/src ./src

# Compile every .java file into out/. No dependency manager: the service uses
# only the JDK, including its built-in HTTP server (ADR-004's lighter option).
RUN mkdir -p out && javac -d out $(find src -name "*.java")

# ---- Run stage ----
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Compiled classes, then the HTML/JS served from the same process on one port.
COPY --from=build /app/out ./out
COPY static ./static

# Configuration comes only from the environment, so nothing is hard-coded for
# the Week 13 docker-compose environment.
#   PORT             port to listen on (default 8080)
#   SHARED_CORE_URL  Shared Core's root URL; unset turns on the stub fallback
#   DB_URL           recorded for Sprint 2; Sprint 1 keeps profiles in memory
#   STATIC_DIR       where the HTML/JS live (default ./static, so /app/static here)
ENV PORT=8080

EXPOSE 8080

CMD ["java", "-cp", "out", "MentoringApp"]
