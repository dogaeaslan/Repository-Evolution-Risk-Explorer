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
  commits: CommitEvidence[];
};

type AnalysisWarning = {
  code: string;
  message: string;
};

type RepositoryAnalysis = {
  analysisId: string;
  repositoryPath: string;
  branch: string;
  periodStart: string;
  periodEnd: string;
  traversedCommitCount: number;
  analyzedCommitCount: number;
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
});

function formatDate(value: string) {
  return dateFormatter.format(new Date(value));
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
        body: JSON.stringify({ repositoryPath, branch }),
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
        <p>Local Git evidence · Milestone 1</p>
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
          <dt>Period</dt>
          <dd>
            {formatDate(analysis.periodStart)} – {formatDate(analysis.periodEnd)}
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

      {analysis.warnings.length > 0 && (
        <aside className="warning-panel" aria-labelledby="warnings-title">
          <strong id="warnings-title">Analysis policy</strong>
          {analysis.warnings.map((warning) => (
            <p key={warning.code}>{warning.message}</p>
          ))}
        </aside>
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
            {analysis.hotspots.map((hotspot, index) => (
              <tr key={hotspot.fileIdentity}>
                <td className="rank">{String(index + 1).padStart(2, "0")}</td>
                <td className="file-cell">
                  <strong>{hotspot.path}</strong>
                  {hotspot.deleted && <span className="tag">Deleted</span>}
                  {hotspot.historicalPaths.length > 1 && (
                    <small>
                      Previously {hotspot.historicalPaths.slice(0, -1).join(", ")}
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
            ))}
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
