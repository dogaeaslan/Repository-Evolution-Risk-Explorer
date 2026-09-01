package dev.riskexplorer.api;

import dev.riskexplorer.analysis.AnalysisRequest;
import dev.riskexplorer.analysis.AnalysisService;
import dev.riskexplorer.analysis.RepositoryAnalysis;
import java.net.URI;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

  private final AnalysisService analysisService;

  public AnalysisController(AnalysisService analysisService) {
    this.analysisService = analysisService;
  }

  @PostMapping
  public ResponseEntity<RepositoryAnalysis> create(@RequestBody AnalysisRequest request) {
    RepositoryAnalysis result = analysisService.analyze(request);
    return ResponseEntity.created(URI.create("/api/analyses/" + result.analysisId())).body(result);
  }

  @GetMapping("/{analysisId}")
  public RepositoryAnalysis get(@PathVariable String analysisId) {
    return analysisService.get(analysisId);
  }

  @GetMapping(value = "/{analysisId}/export", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<RepositoryAnalysis> export(@PathVariable String analysisId) {
    RepositoryAnalysis result = analysisService.get(analysisId);
    ContentDisposition disposition =
        ContentDisposition.attachment()
            .filename("repository-analysis-" + result.analysisId() + ".json")
            .build();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .body(result);
  }
}
