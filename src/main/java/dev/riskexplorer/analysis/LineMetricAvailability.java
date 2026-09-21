package dev.riskexplorer.analysis;

public enum LineMetricAvailability {
  AVAILABLE,
  PARTIAL,
  UNAVAILABLE;

  public static LineMetricAvailability fromChangeCounts(
      int commitCount, int unavailableLineMetricChangeCount) {
    if (commitCount < 0
        || unavailableLineMetricChangeCount < 0
        || unavailableLineMetricChangeCount > commitCount) {
      throw new IllegalArgumentException(
          "Unavailable line-metric change count must be within the commit count.");
    }
    if (unavailableLineMetricChangeCount == 0) {
      return AVAILABLE;
    }
    return unavailableLineMetricChangeCount == commitCount ? UNAVAILABLE : PARTIAL;
  }
}
