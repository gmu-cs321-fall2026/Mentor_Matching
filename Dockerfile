# ---- Build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copy source files into the container
COPY src ./src

# Compile all .java files into the out/ directory
RUN mkdir -p out && javac -d out $(find src -name "*.java")

# ---- Run stage ----
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy compiled classes from the build stage
COPY --from=build /app/out .

# Change "Main" to your actual entry-point class name (no .java, no package prefix
# unless it's inside a package, e.g. com.example.Main)
CMD ["java", "Main"]
