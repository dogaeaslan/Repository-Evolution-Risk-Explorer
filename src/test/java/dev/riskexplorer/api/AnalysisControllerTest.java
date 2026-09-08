package dev.riskexplorer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.riskexplorer.analysis.AnalysisException;
import dev.riskexplorer.analysis.AnalysisRequest;
import dev.riskexplorer.analysis.AnalysisScope;
import dev.riskexplorer.analysis.AnalysisService;
import dev.riskexplorer.analysis.AnalysisWarning;
import dev.riskexplorer.analysis.AnalysisWarningCode;
import dev.riskexplorer.analysis.CommitEvidence;
import dev.riskexplorer.analysis.FileChangeFrequency;
import dev.riskexplorer.analysis.LineMetricAvailability;
import dev.riskexplorer.analysis.MergePolicy;
import dev.riskexplorer.analysis.RepositoryAnalysis;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
        .andExpect(jsonPath("$.scope.mergePolicy").value("EXCLUDE_MERGE_DIFFS"))
        .andExpect(jsonPath("$.hotspots[0].path").value("src/HighChurn.java"))
        .andExpect(jsonPath("$.hotspots[0].binaryChangeCount").value(1))
        .andExpect(jsonPath("$.hotspots[0].lineMetricAvailability").value("UNAVAILABLE"))
        .andExpect(jsonPath("$.hotspots[0].commits[0].commitId").value("abc123"))
        .andExpect(jsonPath("$.warnings[0].code").value("SHALLOW_HISTORY"))
        .andExpect(jsonPath("$.warnings[0].category").value("DATA_QUALITY"))
        .andExpect(jsonPath("$.warnings[0].severity").value("WARNING"))
        .andExpect(jsonPath("$.warnings[0].occurrenceCount").value(1))
        .andExpect(jsonPath("$.warnings[1].code").value("BINARY_CONTENT"))
        .andExpect(jsonPath("$.warnings[1].occurrenceCount").value(1));

    ArgumentCaptor<AnalysisRequest> requestCaptor = ArgumentCaptor.forClass(AnalysisRequest.class);
    verify(analysisService).analyze(requestCaptor.capture());
    assertThat(requestCaptor.getValue().repositoryPath()).isEqualTo("demo-repository");
    assertThat(requestCaptor.getValue().branch()).isEqualTo("main");
    assertThat(requestCaptor.getValue().fromInclusive()).isNull();
    assertThat(requestCaptor.getValue().toExclusive()).isNull();
    assertThat(requestCaptor.getValue().exclusionPatterns()).containsExactly("generated/**");

    mockMvc
        .perform(get("/api/analyses/analysis-1/export"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/json"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    "attachment; filename=\"repository-analysis-analysis-1.json\""))
        .andExpect(jsonPath("$.analysisId").value("analysis-1"))
        .andExpect(jsonPath("$.warnings[0].code").value("SHALLOW_HISTORY"))
        .andExpect(jsonPath("$.warnings[0].category").value("DATA_QUALITY"))
        .andExpect(jsonPath("$.warnings[1].code").value("BINARY_CONTENT"));
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
            1,
            LineMetricAvailability.UNAVAILABLE,
            List.of(evidence));
    return new RepositoryAnalysis(
        "analysis-1",
        "C:\\demo-repository",
        "main",
        timestamp,
        timestamp,
        1,
        1,
        new AnalysisScope(
            null, null, List.of("generated/**"), MergePolicy.EXCLUDE_MERGE_DIFFS, 0, 1),
        List.of(hotspot),
        List.of(
            new AnalysisWarning(
                AnalysisWarningCode.SHALLOW_HISTORY,
                1,
                "History for the selected branch is incomplete."),
            new AnalysisWarning(
                AnalysisWarningCode.BINARY_CONTENT,
                1,
                "One binary change has unavailable line metrics.")));
  }
}
