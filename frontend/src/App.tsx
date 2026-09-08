import { useState, type FormEvent } from "react";

type CommitEvidence = {
  commitId: string;
  authoredAt: string;
  authorName: string;
  message: string;
};

type FileChangeFrequency = {
  fileIdentity: string;
  path: string;
  historicalPaths: string[];
  deleted: boolean;
  commitCount: number;
  binaryChangeCount: number;
  lineMetricAvailability: "AVAILABLE" | "PARTIAL" | "UNAVAILABLE";
  commits: CommitEvidence[];
};

type AnalysisWarningCode =
  | "MERGE_DIFFS_EXCLUDED"
  | "DATE_RANGE_APPLIED"
  | "PATHS_EXCLUDED"
  | "SHALLOW_HISTORY"
  | "BINARY_CONTENT";

type AnalysisWarning = {
  code: AnalysisWarningCode;
  category: "POLICY" | "DATA_QUALITY";
  severity: "INFO" | "WARNING";
  occurrenceCount: number;
  message: string;
};

type AnalysisScope = {
  fromInclusive: string | null;
  toExclusive: string | null;
  exclusionPatterns: string[];
  mergePolicy: string;
  dateExcludedCommitCount: number;
  pathExcludedFileChangeCount: number;
};

type RepositoryAnalysis = {
  analysisId: string;
  repositoryPath: string;
  branch: string;
  periodStart: string | null;
  periodEnd: string | null;
  traversedCommitCount: number;
  analyzedCommitCount: number;
  scope: AnalysisScope;
  hotspots: FileChangeFrequency[];
  warnings: AnalysisWarning[];
};

type ProblemDetail = {
  detail?: string;
};

const dateFormatter = new Intl.DateTimeFormat("en", {
  day: "2-digit",
  month: "short",
  year: "numeric",
  timeZone: "UTC",
});

function formatDate(value: string) {
  return dateFormatter.format(new Date(value));
}

function startOfUtcDate(value: string) {
  return value === "" ? null : `${value}T00:00:00Z`;
}

function afterUtcDate(value: string) {
  if (value === "") {
    return null;
  }
  const exclusiveEnd = new Date(`${value}T00:00:00Z`);
  exclusiveEnd.setUTCDate(exclusiveEnd.getUTCDate() + 1);
  return exclusiveEnd.toISOString();
}

function parseExclusionPatterns(value: string) {
  return value
    .split(/\r?\n/)
    .map((pattern) => pattern.trim())
    .filter((pattern) => pattern.length > 0);
}

function formatRequestedPeriod(scope: AnalysisScope) {
  if (scope.fromInclusive === null && scope.toExclusive === null) {
    return "All reachable dates";
  }
  const start =
    scope.fromInclusive === null
      ? "First commit"
      : `From ${formatDate(scope.fromInclusive)}`;
  const end =
    scope.toExclusive === null
      ? "through branch tip"
      : `before ${formatDate(scope.toExclusive)}`;
  return `${start}, ${end} (UTC)`;
}

async function errorMessage(response: Response) {
  try {
    const problem = (await response.json()) as ProblemDetail;
    return problem.detail ?? "The repository could not be analyzed.";
  } catch {
    return "The repository could not be analyzed.";
  }
}

export function App() {
  const [repositoryPath, setRepositoryPath] = useState("demo-repository");
  const [branch, setBranch] = useState("main");
  const [fromDate, setFromDate] = useState("");
  const [throughDate, setThroughDate] = useState("");
  const [exclusionPatterns, setExclusionPatterns] = useState("generated/**");
  const [analysis, setAnalysis] = useState<RepositoryAnalysis | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submitAnalysis(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setIsLoading(true);
    setError(null);

    try {
      const response = await fetch("/api/analyses", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          repositoryPath,
          branch,
          fromInclusive: startOfUtcDate(fromDate),
          toExclusive: afterUtcDate(throughDate),
          exclusionPatterns: parseExclusionPatterns(exclusionPatterns),
        }),
      });
      if (!response.ok) {
        throw new Error(await errorMessage(response));
      }
      setAnalysis((await response.json()) as RepositoryAnalysis);
    } catch (cause) {
      setAnalysis(null);
      setError(
        cause instanceof Error
          ? cause.message
          : "The repository could not be analyzed.",
      );
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <main>
      <header className="masthead">
        <a
          className="wordmark"
          href="#top"
          aria-label="Repository Evolution Risk Explorer"
        >
          <span aria-hidden="true">RE</span>
          <strong>Risk Explorer</strong>
        </a>
        <p>Local Git evidence · Milestone 2 scope</p>
      </header>

      <section className="hero" id="top" aria-labelledby="page-title">
        <div>
          <p className="eyebrow">Change-frequency analysis</p>
          <h1 id="page-title">Find where change keeps returning.</h1>
          <p className="intro">
            Rank files by the number of commits that touched them, then inspect the
            exact commit evidence behind every count.
          </p>
        </div>

        <form className="analysis-form" onSubmit={submitAnalysis} aria-busy={isLoading}>
          <div className="field">
            <label htmlFor="repository-path">Local repository path</label>
            <input
              id="repository-path"
              name="repositoryPath"
              value={repositoryPath}
              onChange={(event) => setRepositoryPath(event.target.value)}
              placeholder="C:\\projects\\example"
              required
            />
            <small>Nothing is uploaded or executed.</small>
          </div>
          <div className="field branch-field">
            <label htmlFor="branch">Branch</label>
            <input
              id="branch"
              name="branch"
              value={branch}
              onChange={(event) => setBranch(event.target.value)}
              required
            />
          </div>
          <div className="date-fields">
            <div className="field">
              <label htmlFor="from-date">From date (UTC)</label>
              <input
                id="from-date"
                name="fromDate"
                type="date"
                value={fromDate}
                max={throughDate || undefined}
                onChange={(event) => setFromDate(event.target.value)}
              />
            </div>
            <div className="field">
              <label htmlFor="through-date">Through date (UTC)</label>
              <input
                id="through-date"
                name="throughDate"
                type="date"
                value={throughDate}
                min={fromDate || undefined}
                onChange={(event) => setThroughDate(event.target.value)}
              />
            </div>
          </div>
          <div className="field exclusions-field">
            <label htmlFor="exclusion-patterns">Exclude Git paths</label>
            <textarea
              id="exclusion-patterns"
              name="exclusionPatterns"
              rows={2}
              value={exclusionPatterns}
              onChange={(event) => setExclusionPatterns(event.target.value)}
            />
            <small>
              One portable glob per line. * stays within a path segment; ** crosses
              folders.
            </small>
          </div>
          <button type="submit" disabled={isLoading}>
            {isLoading ? "Reading history…" : "Analyze repository"}
          </button>
        </form>
      </section>

      {error !== null && (
        <div className="error-panel" role="alert">
          <strong>Analysis could not start.</strong>
          <span>{error}</span>
        </div>
      )}

      {analysis === null ? (
        <section className="empty-state" aria-labelledby="first-run-title">
          <div>
            <p className="eyebrow">First vertical slice</p>
            <h2 id="first-run-title">One metric, fully traceable.</h2>
          </div>
          <ol>
            <li>
              <span>01</span>
              Traverse ordinary commits reachable from the selected branch.
            </li>
            <li>
              <span>02</span>
              Preserve file identity through detected renames.
            </li>
            <li>
              <span>03</span>
              Show every commit behind the resulting frequency count.
            </li>
          </ol>
        </section>
      ) : (
        <AnalysisResults analysis={analysis} />
      )}
    </main>
  );
}

function AnalysisResults({ analysis }: { analysis: RepositoryAnalysis }) {
  const dataQualityWarnings = analysis.warnings.filter(
    (warning) => warning.category === "DATA_QUALITY",
  );
  const policyWarnings = analysis.warnings.filter(
    (warning) => warning.category === "POLICY",
  );

  return (
    <section className="results" aria-labelledby="results-title">
      <div className="results-heading">
        <div>
          <p className="eyebrow">Completed analysis</p>
          <h2 id="results-title">Files ranked by change frequency</h2>
        </div>
        <a
          className="export-link"
          href={`/api/analyses/${analysis.analysisId}/export`}
          download
        >
          Export JSON
        </a>
      </div>

      <dl className="scope-summary">
        <div>
          <dt>Repository</dt>
          <dd title={analysis.repositoryPath}>{analysis.repositoryPath}</dd>
        </div>
        <div>
          <dt>Branch</dt>
          <dd>{analysis.branch}</dd>
        </div>
        <div>
          <dt>Eligible period</dt>
          <dd>
            {analysis.periodStart === null || analysis.periodEnd === null
              ? "No eligible commits"
              : `${formatDate(analysis.periodStart)} – ${formatDate(analysis.periodEnd)}`}
          </dd>
        </div>
        <div>
          <dt>Commits</dt>
          <dd>
            {analysis.analyzedCommitCount} analyzed / {analysis.traversedCommitCount}{" "}
            traversed
          </dd>
        </div>
      </dl>

      <dl className="filter-summary">
        <div>
          <dt>Date scope</dt>
          <dd>{formatRequestedPeriod(analysis.scope)}</dd>
          <small>
            {analysis.scope.dateExcludedCommitCount} ordinary commits excluded by date
          </small>
        </div>
        <div>
          <dt>Path exclusions</dt>
          <dd>
            {analysis.scope.exclusionPatterns.length === 0
              ? "None"
              : analysis.scope.exclusionPatterns.join(", ")}
          </dd>
          <small>
            {analysis.scope.pathExcludedFileChangeCount} in-range file changes excluded
          </small>
        </div>
      </dl>

      {analysis.warnings.length > 0 && (
        <div className="warning-stack">
          <WarningPanel
            id="data-quality-warnings-title"
            title="Data quality and limitations"
            warnings={dataQualityWarnings}
            isDataQuality
          />
          <WarningPanel
            id="policy-warnings-title"
            title="Analysis policy"
            warnings={policyWarnings}
          />
        </div>
      )}

      <div className="table-frame">
        <table>
          <thead>
            <tr>
              <th scope="col">Rank</th>
              <th scope="col">File</th>
              <th scope="col">Commits</th>
              <th scope="col">Evidence</th>
            </tr>
          </thead>
          <tbody>
            {analysis.hotspots.length === 0 ? (
              <tr>
                <td className="no-results" colSpan={4}>
                  No file changes matched the selected scope.
                </td>
              </tr>
            ) : (
              analysis.hotspots.map((hotspot, index) => (
                <tr key={hotspot.fileIdentity}>
                  <td className="rank">{String(index + 1).padStart(2, "0")}</td>
                  <td className="file-cell">
                    <strong>{hotspot.path}</strong>
                    {hotspot.deleted && <span className="tag">Deleted</span>}
                    {hotspot.lineMetricAvailability !== "AVAILABLE" && (
                      <span className="tag limitation-tag">
                        {hotspot.lineMetricAvailability === "PARTIAL"
                          ? "Line metrics partial"
                          : "Line metrics unavailable"}
                        {` · ${hotspot.binaryChangeCount} binary ${
                          hotspot.binaryChangeCount === 1 ? "change" : "changes"
                        }`}
                      </span>
                    )}
                    {hotspot.historicalPaths.length > 1 && (
                      <small>
                        Previously{" "}
                        {hotspot.historicalPaths
                          .filter((historicalPath) => historicalPath !== hotspot.path)
                          .join(", ")}
                      </small>
                    )}
                  </td>
                  <td>
                    <span className="frequency">{hotspot.commitCount}</span>
                  </td>
                  <td>
                    <details>
                      <summary>Inspect commits</summary>
                      <ol className="commit-list">
                        {hotspot.commits.map((commit) => (
                          <li key={commit.commitId}>
                            <code>{commit.commitId.slice(0, 8)}</code>
                            <span>
                              <strong>{commit.message}</strong>
                              <small>
                                {commit.authorName} · {formatDate(commit.authoredAt)}
                              </small>
                            </span>
                          </li>
                        ))}
                      </ol>
                    </details>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <p className="qualification">
        Change frequency identifies where activity concentrates. It does not prove that
        a file is defective or poorly designed.
      </p>
    </section>
  );
}

function WarningPanel({
  id,
  title,
  warnings,
  isDataQuality = false,
}: {
  id: string;
  title: string;
  warnings: AnalysisWarning[];
  isDataQuality?: boolean;
}) {
  if (warnings.length === 0) {
    return null;
  }

  return (
    <aside
      className={`warning-panel${isDataQuality ? " data-quality-panel" : ""}`}
      aria-labelledby={id}
    >
      <strong id={id}>{title}</strong>
      <ul>
        {warnings.map((warning) => (
          <li key={warning.code}>{warning.message}</li>
        ))}
      </ul>
    </aside>
  );
}
