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
  hotspots: [
    {
      fileIdentity: "file-1",
      path: "src/HighChurn.java",
      historicalPaths: ["src/HighChurn.java"],
      deleted: false,
      commitCount: 6,
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
      message: "1 merge commit diff was excluded.",
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
    expect(screen.getByText("hotfix: stabilize parser recovery")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Export JSON" })).toHaveAttribute(
      "href",
      "/api/analyses/analysis-1/export",
    );
    expect(fetchMock).toHaveBeenCalledWith("/api/analyses", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ repositoryPath: "demo-repository", branch: "main" }),
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
