# Repository Evolution Risk Explorer

Repository Evolution Risk Explorer is a local-first portfolio application for examining Git history, explaining repository-evolution risk signals, and turning evidence into qualified follow-up recommendations.

## Current status

Milestone 1 is complete. Milestone 2 is in progress with configurable UTC date ranges, portable Git-path exclusions, effective-scope reporting, repository validation, branch traversal, rename-aware file identity, explicit merge-diff policy, and structured data-quality warnings. The application ranks file change frequency, exposes every contributing commit, reports selected-branch shallow-history boundaries, binary-content limitations, and branch-tip deleted files, and exports the same traceable result as JSON.

## Prerequisites

- Java 21
- Git
- Internet access during the first build so Gradle can download the pinned Node.js runtime and project dependencies

Gradle and Node.js do not need to be installed globally; their pinned versions are managed by the build.

## Run the first analysis

Create the deterministic demonstration repository. The task only replaces a repository previously created by the same generator; it refuses to overwrite an unrelated directory.

On Windows:

```powershell
.\gradlew.bat createDemoRepository
.\gradlew.bat bootRun
```

On Linux or macOS:

```bash
./gradlew createDemoRepository
./gradlew bootRun
```

Then open `http://localhost:8080` and select **Analyze repository**. The form defaults to `demo-repository` on `main` and excludes `generated/**`. The generated history contains 14 traversed commits, 13 ordinary commits included in the metric, a six-commit high-churn file, a rename, multiple authors, an excluded generated file, and an excluded merge diff.

Each ranked file expands to its supporting commits. **Export JSON** downloads the same traceable result for use outside the interface. Change frequency measures concentrated activity; it is evidence for investigation, not proof of defective design.

## Run the bundled application with another repository

On Windows:

```powershell
.\gradlew.bat bootRun
```

On Linux or macOS:

```bash
./gradlew bootRun
```

Then open `http://localhost:8080`, enter the local repository path and an exact local branch name, and start the analysis. You may select an inclusive start and through date in UTC and configure one exclusion glob per line. `*` matches within one Git-path segment, `?` matches one character within a segment, and `**` crosses folders. Clear the exclusion field to include generated paths. The completed result reports the exact normalized patterns and how many commits or file changes they excluded.

The API represents date scope as an optional half-open interval: `fromInclusive` is included and `toExclusive` is excluded. The browser's **Through date** control converts the selected UTC day to the following midnight, so the whole selected day remains eligible. The backend status endpoint is available at `http://localhost:8080/api/system/status`.

If the selected branch reaches a shallow-history boundary, the result is explicitly marked as incomplete. The boundary commit itself is not diffed against an empty tree because that would incorrectly classify every file already present at the boundary as newly added. Descendant commits with an available parent tree remain eligible. Fetch the repository's full history and analyze it again for complete metrics.

Binary content is detected from Git blobs rather than filename extensions. Binary changes remain valid change-frequency evidence, but line additions, deletions, and churn are unavailable for those changes. Each file reports whether future line-based metrics are fully available, partially available, or unavailable, together with its binary-change count.

### Deleted-file semantics

A file identity is marked as deleted when it is absent at the selected branch tip, not merely absent at the end of the requested date range. Date ranges and path exclusions control which changes contribute metric evidence; they do not redefine the branch-tip lifecycle state. Consequently, a file with eligible historical changes can be reported as deleted even when its deletion occurred after the requested period or was itself excluded.

An eligible deletion remains valid change-frequency evidence, and its historical diff can support additions, deletions, and churn when the required Git blobs are available. Metrics that require current branch-tip content, such as current size or complexity, are unavailable for a deleted identity. A rename followed by deletion retains the last known path and complete path history. If the same path is later added again, it starts a new file identity; the deleted historical identity and the active replacement remain separate. File identities with no eligible evidence are omitted from results and do not contribute deletion notices.

When at least one displayed file identity is absent at the branch tip, the API and JSON export include a `DELETED_FILES_AT_BRANCH_TIP` data-quality notice whose occurrence count is the number of affected identities. The dashboard labels each affected row **Deleted at branch tip** while keeping its historical commit evidence available for inspection.

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
