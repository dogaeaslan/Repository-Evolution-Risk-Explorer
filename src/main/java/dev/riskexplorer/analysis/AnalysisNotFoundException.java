package dev.riskexplorer.analysis;

import java.io.Serial;

public class AnalysisNotFoundException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public AnalysisNotFoundException(String analysisId) {
    super("No completed analysis exists with id '" + analysisId + "'.");
  }
}
