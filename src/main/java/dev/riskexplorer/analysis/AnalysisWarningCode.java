package dev.riskexplorer.analysis;

public enum AnalysisWarningCode {
  MERGE_DIFFS_EXCLUDED(AnalysisWarningCategory.POLICY, AnalysisWarningSeverity.INFO),
  DATE_RANGE_APPLIED(AnalysisWarningCategory.POLICY, AnalysisWarningSeverity.INFO),
  PATHS_EXCLUDED(AnalysisWarningCategory.POLICY, AnalysisWarningSeverity.INFO),
  SHALLOW_HISTORY(AnalysisWarningCategory.DATA_QUALITY, AnalysisWarningSeverity.WARNING),
  BINARY_CONTENT(AnalysisWarningCategory.DATA_QUALITY, AnalysisWarningSeverity.WARNING);

  private final AnalysisWarningCategory category;
  private final AnalysisWarningSeverity severity;

  AnalysisWarningCode(AnalysisWarningCategory category, AnalysisWarningSeverity severity) {
    this.category = category;
    this.severity = severity;
  }

  public AnalysisWarningCategory category() {
    return category;
  }

  public AnalysisWarningSeverity severity() {
    return severity;
  }
}
