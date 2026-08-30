# Repository Evolution Risk Explorer

Repository Evolution Risk Explorer is a local-first portfolio application for examining Git history, explaining repository-evolution risk signals, and turning evidence into qualified follow-up recommendations.

## Current status

Milestone 0 establishes the Java 21/Spring Boot backend, React/TypeScript/Vite frontend, Gradle build, automated tests, formatting checks, and Windows/Linux CI. Repository analysis begins in Milestone 1.

## Prerequisites

- Java 21
- Git
- Internet access during the first build so Gradle can download the pinned Node.js runtime and project dependencies

Gradle and Node.js do not need to be installed globally; their pinned versions are managed by the build.

## Run the bundled application

On Windows:

```powershell
.\gradlew.bat bootRun
```

On Linux or macOS:

```bash
./gradlew bootRun
```

Then open `http://localhost:8080`. The backend status endpoint is available at `http://localhost:8080/api/system/status`.

## Development workflow

Run the backend without rebuilding the frontend:

```powershell
.\gradlew.bat bootRun -x frontendBuild
```

In another terminal, run the Vite development server through Gradle:

```powershell
.\gradlew.bat frontendDev
```

The Vite server runs at `http://localhost:5173` and proxies `/api` requests to Spring Boot.

Vite is the frontend development server and production bundler. It gives React fast feedback during development, checks module imports, and creates the optimized static files that Gradle packages into the Spring Boot application. The build downloads its own pinned Node.js runtime, so learning or maintaining the frontend does not require a separate global Node installation.

## Quality checks

```powershell
.\gradlew.bat check
```

The check lifecycle runs Java tests, Java formatting verification, strict TypeScript compilation, Prettier verification, and frontend tests. The complete build additionally produces an executable Spring Boot archive containing the frontend.

The authoritative requirements and milestone gates are in [the product specification](01-repository-evolution-risk-explorer.md).
