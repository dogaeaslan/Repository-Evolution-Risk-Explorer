import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { App } from "./App";

const completedAnalysis = {
  analysisId: "analysis-1",
  repositoryPath: "C:\\demo-repository",
  branch: "main",
  periodStart: "2025-01-01T09:00:00Z",
  periodEnd: "2025-01-14T09:00:00Z",
  traversedCommitCount: 14,
  analyzedCommitCount: 13,
  scope: {
    fromInclusive: null,
    toExclusive: null,
    exclusionPatterns: ["generated/**"],
    mergePolicy: "EXCLUDE_MERGE_DIFFS",
    dateExcludedCommitCount: 0,
    pathExcludedFileChangeCount: 1,
  },
  hotspots: [
    {
      fileIdentity: "file-1",
      path: "src/HighChurn.java",
      historicalPaths: ["src/HighChurn.java"],
      deleted: false,
      commitCount: 6,
      binaryChangeCount: 1,
      lineMetricAvailability: "PARTIAL",
      commits: [
        {
          commitId: "1234567890abcdef",
          authoredAt: "2025-01-14T09:00:00Z",
          authorName: "Carol Example",
          message: "hotfix: stabilize parser recovery",
        },
      ],
    },
  ],
  warnings: [
    {
      code: "MERGE_DIFFS_EXCLUDED",
      category: "POLICY",
      severity: "INFO",
      occurrenceCount: 1,
      message: "1 merge commit diff was excluded.",
    },
    {
      code: "BINARY_CONTENT",
      category: "DATA_QUALITY",
      severity: "WARNING",
      occurrenceCount: 1,
      message:
        "1 in-scope binary file change was included in frequency; line metrics are unavailable for that change.",
    },
    {
      code: "SHALLOW_HISTORY",
      category: "DATA_QUALITY",
      severity: "WARNING",
      occurrenceCount: 1,
      message:
        "History is incomplete. Fetch the full repository history and analyze again.",
    },
  ],
};

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("App", () => {
  it("starts with the deterministic demonstration repository", () => {
    render(<App />);

    expect(
      screen.getByRole("heading", { name: "Find where change keeps returning." }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Local repository path")).toHaveValue(
      "demo-repository",
    );
    expect(screen.getByLabelText("Branch")).toHaveValue("main");
    expect(screen.getByLabelText("From date (UTC)")).toHaveValue("");
    expect(screen.getByLabelText("Through date (UTC)")).toHaveValue("");
    expect(screen.getByLabelText("Exclude Git paths")).toHaveValue("generated/**");
    expect(
      screen.getByRole("heading", { name: "One metric, fully traceable." }),
    ).toBeInTheDocument();
  });

  it("submits the scope and renders ranked evidence with an export link", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => completedAnalysis,
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    fireEvent.click(screen.getByRole("button", { name: "Analyze repository" }));

    expect(
      await screen.findByRole("heading", { name: "Files ranked by change frequency" }),
    ).toBeInTheDocument();
    expect(screen.getByText("src/HighChurn.java")).toBeInTheDocument();
    expect(screen.getByText("6")).toBeInTheDocument();
    expect(screen.getByText("1 merge commit diff was excluded.")).toBeInTheDocument();
    expect(
      screen.getByText("Line metrics partial · 1 binary change"),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "1 in-scope binary file change was included in frequency; line metrics are unavailable for that change.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("Data quality and limitations")).toBeInTheDocument();
    expect(
      screen.getByText(
        "History is incomplete. Fetch the full repository history and analyze again.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("All reachable dates")).toBeInTheDocument();
    expect(screen.getAllByText("generated/**")).toHaveLength(2);
    expect(screen.getByText("hotfix: stabilize parser recovery")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Export JSON" })).toHaveAttribute(
      "href",
      "/api/analyses/analysis-1/export",
    );
    expect(fetchMock).toHaveBeenCalledWith("/api/analyses", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        repositoryPath: "demo-repository",
        branch: "main",
        fromInclusive: null,
        toExclusive: null,
        exclusionPatterns: ["generated/**"],
      }),
    });
  });

  it("converts inclusive UTC dates and line-separated exclusions into API scope", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => completedAnalysis,
    });
    vi.stubGlobal("fetch", fetchMock);
    render(<App />);

    fireEvent.change(screen.getByLabelText("From date (UTC)"), {
      target: { value: "2025-01-08" },
    });
    fireEvent.change(screen.getByLabelText("Through date (UTC)"), {
      target: { value: "2025-01-10" },
    });
    fireEvent.change(screen.getByLabelText("Exclude Git paths"), {
      target: { value: " generated/**\n\nsrc/Pair?.java " },
    });
    fireEvent.click(screen.getByRole("button", { name: "Analyze repository" }));

    await screen.findByRole("heading", { name: "Files ranked by change frequency" });
    expect(fetchMock).toHaveBeenCalledWith("/api/analyses", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        repositoryPath: "demo-repository",
        branch: "main",
        fromInclusive: "2025-01-08T00:00:00Z",
        toExclusive: "2025-01-11T00:00:00.000Z",
        exclusionPatterns: ["generated/**", "src/Pair?.java"],
      }),
    });
  });

  it("shows the API problem detail when analysis fails", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        json: async () => ({ detail: "The selected branch does not exist." }),
      }),
    );
    render(<App />);

    fireEvent.click(screen.getByRole("button", { name: "Analyze repository" }));

    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent(
        "The selected branch does not exist.",
      ),
    );
  });
});
