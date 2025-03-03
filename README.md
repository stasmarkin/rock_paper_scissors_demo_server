
# Rock Paper Scissors Demo Server

A multiplayer "Rock, Paper, Scissors" game server built with Kotlin, coroutines, and Netty.

## Overview

This project implements a server for the classic "Rock, Paper, Scissors" game, allowing multiple clients to connect and play against each other.

## Technologies

- Kotlin
- Coroutines
- Netty
- Google Guice for dependency injection
- Gradle for build management

## Prerequisites

- JDK 17 or higher
- Docker (optional, for containerized deployment)

## Building the Project

```bash
./gradlew build
```

## Running the Server

### Method 1: Using Gradle

```bash
./gradlew run
```

The server will start on port 8080 by default.

### Method 2: Using Docker

Build and run the Docker container:

```bash
docker-compose up -d
```

This will start the server on port 8080, mapped from the container to your host machine.

To stop the server:

```bash
docker-compose down
```

## Running Stress Tests

To run stress tests against the server:

```bash
./gradlew stressTest
```

This will simulate multiple concurrent clients connecting to the server and playing games to evaluate performance under load.

## Connecting to the Server

Just run:
```bash
telnet localhost 8080
```



## License

MIT License
