# Mentor Matching (Subsystem 4) — the whole subsystem as one container.
# Builds and runs with nothing but `docker build` and `docker run`.

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
