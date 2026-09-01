package dev.riskexplorer.analysis;

import java.io.Serial;

public class AnalysisException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  public AnalysisException(String message) {
    super(message);
  }

  public AnalysisException(String message, Throwable cause) {
    super(message, cause);
  }
}
