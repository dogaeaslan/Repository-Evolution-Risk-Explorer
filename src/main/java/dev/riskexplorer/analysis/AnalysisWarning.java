package dev.riskexplorer.analysis;

import java.util.Objects;

public record AnalysisWarning(
    AnalysisWarningCode code,
    AnalysisWarningCategory category,
    AnalysisWarningSeverity severity,
    int occurrenceCount,
    String message) {

  public AnalysisWarning(AnalysisWarningCode code, int occurrenceCount, String message) {
    this(
        code,
        Objects.requireNonNull(code, "Warning code must not be null.").category(),
        code.severity(),
        occurrenceCount,
        message);
  }

  public AnalysisWarning {
    Objects.requireNonNull(code, "Warning code must not be null.");
    Objects.requireNonNull(category, "Warning category must not be null.");
    Objects.requireNonNull(severity, "Warning severity must not be null.");
    if (category != code.category() || severity != code.severity()) {
      throw new IllegalArgumentException("Warning metadata must match its code.");
    }
    if (occurrenceCount < 1) {
      throw new IllegalArgumentException("Warning occurrence count must be positive.");
    }
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("Warning message must not be blank.");
    }
  }
}
