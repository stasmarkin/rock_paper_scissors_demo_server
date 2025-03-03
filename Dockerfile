FROM gradle:7.6-jdk17 AS build

WORKDIR /app

# Copy gradle configuration files
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle

# Copy source code
COPY src ./src

# Build the application
RUN gradle build --no-daemon

FROM openjdk:17-slim AS app

WORKDIR /app

# Copy the built application from the build stage
COPY --from=build /app/build/libs/*.jar ./app.jar

EXPOSE 8080

# Run the application
CMD ["java", "-jar", "app.jar"]
