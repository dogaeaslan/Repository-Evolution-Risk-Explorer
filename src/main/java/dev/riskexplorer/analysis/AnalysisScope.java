package dev.riskexplorer.analysis;

import java.time.Instant;
import java.util.List;

public record AnalysisScope(
    Instant fromInclusive,
    Instant toExclusive,
    List<String> exclusionPatterns,
    MergePolicy mergePolicy,
    int dateExcludedCommitCount,
    int pathExcludedFileChangeCount) {

  public AnalysisScope {
    exclusionPatterns = List.copyOf(exclusionPatterns);
    if (dateExcludedCommitCount < 0 || pathExcludedFileChangeCount < 0) {
      throw new IllegalArgumentException("Analysis scope counts must not be negative.");
    }
  }
}
