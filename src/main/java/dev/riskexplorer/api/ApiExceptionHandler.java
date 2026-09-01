package dev.riskexplorer.api;

import dev.riskexplorer.analysis.AnalysisException;
import dev.riskexplorer.analysis.AnalysisNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(AnalysisException.class)
  public ProblemDetail invalidAnalysisRequest(AnalysisException exception) {
    ProblemDetail detail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    detail.setTitle("Analysis could not start");
    return detail;
  }

  @ExceptionHandler(AnalysisNotFoundException.class)
  public ProblemDetail analysisNotFound(AnalysisNotFoundException exception) {
    ProblemDetail detail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    detail.setTitle("Analysis not found");
    return detail;
  }
}
