package dev.riskexplorer.analysis;

public enum LineMetricAvailability {
  AVAILABLE,
  PARTIAL,
  UNAVAILABLE;

  public static LineMetricAvailability fromChangeCounts(int commitCount, int binaryChangeCount) {
    if (commitCount < 0 || binaryChangeCount < 0 || binaryChangeCount > commitCount) {
      throw new IllegalArgumentException("Binary change count must be within the commit count.");
    }
    if (binaryChangeCount == 0) {
      return AVAILABLE;
    }
    return binaryChangeCount == commitCount ? UNAVAILABLE : PARTIAL;
  }
}
