# NVD Local Server

A standalone Kotlin-based server designed to serve a local copy of the National Vulnerability Database (NVD). It provides an NVD-compatible API and can be used as a containerized service or a local tool to reduce dependency on the public NVD API.

## Features

- **Local NVD Mirror**: Syncs data from the official NVD API and stores it in a local SQLite database.
- **NVD-Compatible API**: Implements the NVD CVE API 2.0 specification for local querying.
- **Datafeed Generation**: Automatically generates `.json.gz` data feeds and metadata files.
- **Two Operation Modes**:
    - **Server Mode**: A persistent Ktor-based server providing API endpoints and background sync.
    - **Single Run Mode**: A one-shot execution to sync data, rebuild feeds, and exit.

## Prerequisites

- **Java 21** or higher.
- **Internet Access**: Required for initial and periodic synchronization with NVD.
- **NVD API Key** (Recommended): To avoid strict rate limits from the public NVD API.

## Setup & Installation

### Build from Source

Clone the repository and build the project using the provided Gradle wrapper:

```bash
./gradlew build
```

The executable JAR will be located in `build/libs/`.

## Configuration

The application is configured via `src/main/resources/application.yaml`. You can also override settings via environment variables (e.g., `NVD_API_KEY`).

Key configuration options:
- `ktor.deployment.port`: The port on which the server runs (default: `8080`).
- `nvd.db.url`: JDBC URL for the SQLite database (default: `jdbc:sqlite:data/nvd.db`).
- `nvd.api.baseUrl`: The source NVD API URL.

### API Key

Set the `NVD_API_KEY` environment variable to increase the sync rate. Without a key, the sync process will be significantly slower to comply with NVD rate limits.

## Usage

### 1. Server Mode (Default)

Starts the Ktor server, provides the API, and schedules background synchronization.

```bash
java -jar build/libs/nvd-local-server-all.jar
# OR via Gradle
./gradlew run
```

### 2. Single Run Mode (One-Shot)

Syncs the local database with the latest NVD data, generates data feeds, and exits immediately. This is ideal for cron jobs or CI/CD pipelines.

```bash
java -jar build/libs/nvd-local-server-all.jar --sync-and-exit
```

## API Endpoints

- **Health Check**: `GET /health`
- **CVE Search (NVD 2.0 compatible)**: `GET /rest/json/cves/2.0`
    - Supports `startIndex`, `resultsPerPage`, `lastModStartDate`, `lastModEndDate`, and `cveId`.
- **Data Mirror**: `GET /nvd/mirror/`
    - Serves generated `.json.gz` feeds and `.meta` files.

## Project Structure

- `data/nvd.db`: SQLite database storing CVE entries.
- `data/mirror/`: Directory containing generated data feeds.
- `src/main/kotlin/`: Source code organized by `api`, `client`, `db`, and `service`.
