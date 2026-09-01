package dev.riskexplorer.analysis;

import java.util.List;

public record FileChangeFrequency(
    String fileIdentity,
    String path,
    List<String> historicalPaths,
    boolean deleted,
    int commitCount,
    List<CommitEvidence> commits) {

  public FileChangeFrequency {
    historicalPaths = List.copyOf(historicalPaths);
    commits = List.copyOf(commits);
  }
}
