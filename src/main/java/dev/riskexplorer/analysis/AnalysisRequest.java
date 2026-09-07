package dev.riskexplorer.analysis;

import java.time.Instant;
import java.util.List;

public record AnalysisRequest(
    String repositoryPath,
    String branch,
    Instant fromInclusive,
    Instant toExclusive,
    List<String> exclusionPatterns) {

  public static final List<String> DEFAULT_EXCLUSION_PATTERNS = List.of("generated/**");

  public AnalysisRequest {
    exclusionPatterns =
        exclusionPatterns == null ? DEFAULT_EXCLUSION_PATTERNS : List.copyOf(exclusionPatterns);
  }

  public AnalysisRequest(String repositoryPath, String branch) {
    this(repositoryPath, branch, null, null, null);
  }
}
