package dev.riskexplorer.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.riskexplorer.demo.DemoRepositoryGenerator;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitChangeFrequencyAnalyzerIntegrationTest {

  @TempDir Path temporaryDirectory;

  @Test
  void analyzesTheKnownDemoHistoryWithTraceableFrequencyEvidence() throws Exception {
    Path repositoryPath = temporaryDirectory.resolve("demo-repository");
    DemoRepositoryGenerator.DemoRepositorySummary fixture =
        DemoRepositoryGenerator.generate(repositoryPath);

    RepositoryAnalysis analysis =
        new GitChangeFrequencyAnalyzer()
            .analyze(new AnalysisRequest(repositoryPath.toString(), "main"));

    assertThat(fixture.commitCount()).isEqualTo(14);
    assertThat(analysis.repositoryPath()).isEqualTo(repositoryPath.toRealPath().toString());
    assertThat(analysis.branch()).isEqualTo("main");
    assertThat(analysis.periodStart()).isEqualTo(Instant.parse("2025-01-01T09:00:00Z"));
    assertThat(analysis.periodEnd()).isEqualTo(Instant.parse("2025-01-14T09:00:00Z"));
    assertThat(analysis.traversedCommitCount()).isEqualTo(14);
    assertThat(analysis.analyzedCommitCount()).isEqualTo(13);
    assertThat(analysis.hotspots()).hasSize(8);
    assertThat(analysis.hotspots())
        .extracting(FileChangeFrequency::commitCount)
        .containsExactly(6, 4, 3, 3, 2, 1, 1, 1);
    assertThat(analysis.hotspots())
        .extracting(FileChangeFrequency::path)
        .doesNotContain("generated/ApiClient.java");
    assertThat(analysis.scope().exclusionPatterns()).containsExactly("generated/**");
    assertThat(analysis.scope().dateExcludedCommitCount()).isZero();
    assertThat(analysis.scope().pathExcludedFileChangeCount()).isEqualTo(1);
    assertThat(analysis.scope().mergePolicy()).isEqualTo(MergePolicy.EXCLUDE_MERGE_DIFFS);

    FileChangeFrequency highestFrequency = analysis.hotspots().getFirst();
    assertThat(highestFrequency.path()).isEqualTo("src/HighChurn.java");
    assertThat(highestFrequency.commitCount()).isEqualTo(6);
    assertThat(highestFrequency.commits()).hasSize(6);
    assertThat(highestFrequency.commits())
        .allSatisfy(
            evidence -> {
              assertThat(evidence.commitId()).hasSize(40);
              assertThat(evidence.authorName()).isNotBlank();
              assertThat(evidence.message()).isNotBlank();
            });

    FileChangeFrequency renamed =
        analysis.hotspots().stream()
            .filter(file -> file.path().equals("src/RenamedComponent.java"))
            .findFirst()
            .orElseThrow();
    assertThat(renamed.commitCount()).isEqualTo(2);
    assertThat(renamed.historicalPaths())
        .containsExactly("src/LegacyName.java", "src/RenamedComponent.java");

    assertThat(analysis.warnings())
        .extracting(AnalysisWarning::code)
        .containsExactly("MERGE_DIFFS_EXCLUDED", "PATHS_EXCLUDED");
  }

  @Test
  void appliesAnInclusiveStartAndExclusiveEndWhileRetainingEarlierRenameIdentity()
      throws Exception {
    Path repositoryPath = temporaryDirectory.resolve("date-filtered-repository");
    DemoRepositoryGenerator.generate(repositoryPath);

    RepositoryAnalysis analysis =
        new GitChangeFrequencyAnalyzer()
            .analyze(
                new AnalysisRequest(
                    repositoryPath.toString(),
                    "main",
                    Instant.parse("2025-01-08T09:00:00Z"),
                    Instant.parse("2025-01-11T09:00:00Z"),
                    null));

    assertThat(analysis.traversedCommitCount()).isEqualTo(14);
    assertThat(analysis.analyzedCommitCount()).isEqualTo(3);
    assertThat(analysis.periodStart()).isEqualTo(Instant.parse("2025-01-08T09:00:00Z"));
    assertThat(analysis.periodEnd()).isEqualTo(Instant.parse("2025-01-10T09:00:00Z"));
    assertThat(analysis.hotspots())
        .extracting(FileChangeFrequency::path)
        .containsExactly("src/HighChurn.java", "src/Ownership.java", "src/RenamedComponent.java");
    assertThat(analysis.hotspots())
        .extracting(FileChangeFrequency::commitCount)
        .containsExactly(1, 1, 1);
    FileChangeFrequency renamed = analysis.hotspots().get(2);
    assertThat(renamed.historicalPaths())
        .containsExactly("src/LegacyName.java", "src/RenamedComponent.java");
    assertThat(analysis.scope().dateExcludedCommitCount()).isEqualTo(10);
    assertThat(analysis.scope().pathExcludedFileChangeCount()).isZero();
    assertThat(analysis.warnings())
        .extracting(AnalysisWarning::code)
        .containsExactly("MERGE_DIFFS_EXCLUDED", "DATE_RANGE_APPLIED");
  }

  @Test
  void acceptsPortableConfigurableExclusionsAndAllowsTheDefaultsToBeDisabled() throws Exception {
    Path repositoryPath = temporaryDirectory.resolve("custom-exclusions-repository");
    DemoRepositoryGenerator.generate(repositoryPath);
    GitChangeFrequencyAnalyzer analyzer = new GitChangeFrequencyAnalyzer();

    RepositoryAnalysis customExclusions =
        analyzer.analyze(
            new AnalysisRequest(
                repositoryPath.toString(),
                "main",
                null,
                null,
                List.of(" src\\Pair?.java ", "src/Pair?.java")));
    RepositoryAnalysis noExclusions =
        analyzer.analyze(
            new AnalysisRequest(repositoryPath.toString(), "main", null, null, List.of()));

    assertThat(customExclusions.scope().exclusionPatterns()).containsExactly("src/Pair?.java");
    assertThat(customExclusions.scope().pathExcludedFileChangeCount()).isEqualTo(6);
    assertThat(customExclusions.hotspots())
        .extracting(FileChangeFrequency::path)
        .doesNotContain("src/PairA.java", "src/PairB.java")
        .contains("generated/ApiClient.java");
    assertThat(noExclusions.hotspots()).hasSize(9);
  }

  @Test
  void rejectsAnEmptyOrReversedDateIntervalBeforeOpeningTheRepository() {
    Instant boundary = Instant.parse("2025-01-08T09:00:00Z");

    assertThatThrownBy(
            () ->
                new GitChangeFrequencyAnalyzer()
                    .analyze(
                        new AnalysisRequest(
                            temporaryDirectory.toString(), "main", boundary, boundary, List.of())))
        .isInstanceOf(AnalysisException.class)
        .hasMessageContaining("start must be earlier");
  }

  @Test
  void regeneratingTheDemoRepositoryProducesTheSameHeadCommit() throws Exception {
    Path repositoryPath = temporaryDirectory.resolve("demo-repository");
    DemoRepositoryGenerator.generate(repositoryPath);
    String firstHead;
    try (Git git = Git.open(repositoryPath.toFile())) {
      firstHead = git.getRepository().resolve("refs/heads/main").name();
    }

    DemoRepositoryGenerator.generate(repositoryPath);
    String secondHead;
    try (Git git = Git.open(repositoryPath.toFile())) {
      secondHead = git.getRepository().resolve("refs/heads/main").name();
    }

    assertThat(secondHead).isEqualTo(firstHead);
  }
}
