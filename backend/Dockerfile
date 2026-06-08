# Build Stage
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY gradlew .
RUN chmod +x gradlew
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
# Download dependencies first for caching
RUN ./gradlew dependencies --no-daemon || true

COPY src src
RUN ./gradlew bootJar --no-daemon

# Runtime Stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Install streamlink and ffmpeg
RUN apt-get update && \
    apt-get install -y --no-install-recommends streamlink ffmpeg python3 && \
    rm -rf /var/lib/apt/lists/*

# Copy built jar
COPY --from=builder /app/build/libs/*.jar app.jar

# Create downloads folder
RUN mkdir -p downloads/recording downloads/completed

EXPOSE 8080
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
