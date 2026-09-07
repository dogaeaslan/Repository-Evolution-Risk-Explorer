package dev.riskexplorer.analysis;

import java.time.Instant;
import java.util.List;

public record RepositoryAnalysis(
    String analysisId,
    String repositoryPath,
    String branch,
    Instant periodStart,
    Instant periodEnd,
    int traversedCommitCount,
    int analyzedCommitCount,
    AnalysisScope scope,
    List<FileChangeFrequency> hotspots,
    List<AnalysisWarning> warnings) {

  public RepositoryAnalysis {
    hotspots = List.copyOf(hotspots);
    warnings = List.copyOf(warnings);
  }
}
