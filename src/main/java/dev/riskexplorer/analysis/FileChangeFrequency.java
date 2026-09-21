package dev.riskexplorer.analysis;

import java.util.List;

public record FileChangeFrequency(
    String fileIdentity,
    String path,
    List<String> historicalPaths,
    boolean deleted,
    int commitCount,
    int binaryChangeCount,
    int gitlinkChangeCount,
    int unavailableLineMetricChangeCount,
    LineMetricAvailability lineMetricAvailability,
    List<CommitEvidence> commits) {

  public FileChangeFrequency {
    historicalPaths = List.copyOf(historicalPaths);
    commits = List.copyOf(commits);
    LineMetricAvailability expectedAvailability =
        LineMetricAvailability.fromChangeCounts(commitCount, unavailableLineMetricChangeCount);
    if (binaryChangeCount < 0 || gitlinkChangeCount < 0) {
      throw new IllegalArgumentException("Line-metric limitation counts must not be negative.");
    }
    if ((long) binaryChangeCount + gitlinkChangeCount > unavailableLineMetricChangeCount) {
      throw new IllegalArgumentException(
          "Reason-specific limitation counts must fit within unavailable line metrics.");
    }
    if (lineMetricAvailability != expectedAvailability) {
      throw new IllegalArgumentException(
          "Line metric availability must match the recorded change counts.");
    }
  }
}
