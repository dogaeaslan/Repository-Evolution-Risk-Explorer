package dev.riskexplorer.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.riskexplorer.demo.DemoRepositoryGenerator;
import java.nio.file.Path;
import java.time.Instant;
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
    assertThat(analysis.hotspots()).hasSize(9);
    assertThat(analysis.hotspots())
        .extracting(FileChangeFrequency::commitCount)
        .containsExactly(6, 4, 3, 3, 2, 1, 1, 1, 1);

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
        .singleElement()
        .extracting(AnalysisWarning::code)
        .isEqualTo("MERGE_DIFFS_EXCLUDED");
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
