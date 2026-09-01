package dev.riskexplorer.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.riskexplorer.analysis.AnalysisException;
import dev.riskexplorer.analysis.AnalysisRequest;
import dev.riskexplorer.analysis.AnalysisService;
import dev.riskexplorer.analysis.CommitEvidence;
import dev.riskexplorer.analysis.FileChangeFrequency;
import dev.riskexplorer.analysis.RepositoryAnalysis;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AnalysisControllerTest {

  private AnalysisService analysisService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    analysisService = mock(AnalysisService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AnalysisController(analysisService))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void createsAndExportsATraceableAnalysis() throws Exception {
    RepositoryAnalysis result = sampleAnalysis();
    when(analysisService.analyze(any(AnalysisRequest.class))).thenReturn(result);
    when(analysisService.get(result.analysisId())).thenReturn(result);

    mockMvc
        .perform(
            post("/api/analyses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                        {"repositoryPath":"demo-repository","branch":"main"}
                                        """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/analyses/analysis-1"))
        .andExpect(jsonPath("$.branch").value("main"))
        .andExpect(jsonPath("$.hotspots[0].path").value("src/HighChurn.java"))
        .andExpect(jsonPath("$.hotspots[0].commits[0].commitId").value("abc123"));

    mockMvc
        .perform(get("/api/analyses/analysis-1/export"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/json"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    "attachment; filename=\"repository-analysis-analysis-1.json\""))
        .andExpect(jsonPath("$.analysisId").value("analysis-1"));
  }

  @Test
  void mapsInvalidAnalysisInputToAnActionableProblemResponse() throws Exception {
    when(analysisService.analyze(any(AnalysisRequest.class)))
        .thenThrow(new AnalysisException("Select a local Git repository."));

    mockMvc
        .perform(
            post("/api/analyses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repositoryPath\":\"\",\"branch\":\"main\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Analysis could not start"))
        .andExpect(jsonPath("$.detail").value("Select a local Git repository."));
  }

  private static RepositoryAnalysis sampleAnalysis() {
    Instant timestamp = Instant.parse("2025-01-14T09:00:00Z");
    CommitEvidence evidence =
        new CommitEvidence("abc123", timestamp, "Carol Example", "hotfix parser");
    FileChangeFrequency hotspot =
        new FileChangeFrequency(
            "file-1",
            "src/HighChurn.java",
            List.of("src/HighChurn.java"),
            false,
            1,
            List.of(evidence));
    return new RepositoryAnalysis(
        "analysis-1",
        "C:\\demo-repository",
        "main",
        timestamp,
        timestamp,
        1,
        1,
        List.of(hotspot),
        List.of());
  }
}
