package dev.riskexplorer.analysis;

import java.util.List;

public record FileChangeFrequency(
    String fileIdentity,
    String path,
    List<String> historicalPaths,
    boolean deleted,
    int commitCount,
    int binaryChangeCount,
    LineMetricAvailability lineMetricAvailability,
    List<CommitEvidence> commits) {

  public FileChangeFrequency {
    historicalPaths = List.copyOf(historicalPaths);
    commits = List.copyOf(commits);
    LineMetricAvailability expectedAvailability =
        LineMetricAvailability.fromChangeCounts(commitCount, binaryChangeCount);
    if (lineMetricAvailability != expectedAvailability) {
      throw new IllegalArgumentException(
          "Line metric availability must match the recorded change counts.");
    }
  }
}
