package dev.riskexplorer.analysis;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class AnalysisService {

  private final GitChangeFrequencyAnalyzer analyzer;
  private final Map<String, RepositoryAnalysis> completedAnalyses = new ConcurrentHashMap<>();

  public AnalysisService(GitChangeFrequencyAnalyzer analyzer) {
    this.analyzer = analyzer;
  }

  public RepositoryAnalysis analyze(AnalysisRequest request) {
    RepositoryAnalysis result = analyzer.analyze(request);
    completedAnalyses.put(result.analysisId(), result);
    return result;
  }

  public RepositoryAnalysis get(String analysisId) {
    RepositoryAnalysis result = completedAnalyses.get(analysisId);
    if (result == null) {
      throw new AnalysisNotFoundException(analysisId);
    }
    return result;
  }
}
