package dev.riskexplorer.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.riskexplorer.demo.DemoRepositoryGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
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
    assertThat(analysis.hotspots())
        .allSatisfy(
            hotspot -> {
              assertThat(hotspot.binaryChangeCount()).isZero();
              assertThat(hotspot.lineMetricAvailability())
                  .isEqualTo(LineMetricAvailability.AVAILABLE);
            });
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
        .containsExactly(
            AnalysisWarningCode.MERGE_DIFFS_EXCLUDED, AnalysisWarningCode.PATHS_EXCLUDED);
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
        .containsExactly(
            AnalysisWarningCode.MERGE_DIFFS_EXCLUDED, AnalysisWarningCode.DATE_RANGE_APPLIED);
  }

  @Test
  void reportsShallowHistoryAndOmitsTheUnreliableBoundaryDiff() throws Exception {
    Path repositoryPath = temporaryDirectory.resolve("shallow-repository");
    DemoRepositoryGenerator.generate(repositoryPath);

    try (Git git = Git.open(repositoryPath.toFile());
        RevWalk walk = new RevWalk(git.getRepository())) {
      ObjectId headId = git.getRepository().resolve(Constants.HEAD);
      RevCommit head = walk.parseCommit(headId);
      ObjectId shallowBoundary = head.getParent(0).getId();
      git.getRepository().getObjectDatabase().setShallowCommits(Set.of(shallowBoundary));
    }

    RepositoryAnalysis analysis =
        new GitChangeFrequencyAnalyzer()
            .analyze(new AnalysisRequest(repositoryPath.toString(), "main"));

    assertThat(analysis.traversedCommitCount()).isEqualTo(2);
    assertThat(analysis.analyzedCommitCount()).isEqualTo(1);
    assertThat(analysis.hotspots())
        .singleElement()
        .satisfies(
            hotspot -> {
              assertThat(hotspot.path()).isEqualTo("src/HighChurn.java");
              assertThat(hotspot.commitCount()).isEqualTo(1);
            });
    assertThat(analysis.warnings())
        .singleElement()
        .satisfies(
            warning -> {
              assertThat(warning.code()).isEqualTo(AnalysisWarningCode.SHALLOW_HISTORY);
              assertThat(warning.category()).isEqualTo(AnalysisWarningCategory.DATA_QUALITY);
              assertThat(warning.severity()).isEqualTo(AnalysisWarningSeverity.WARNING);
              assertThat(warning.occurrenceCount()).isEqualTo(1);
              assertThat(warning.message())
                  .contains("available parent history", "Fetch the full repository history");
            });
  }

  @Test
  void reportsBinaryChangesWhilePreservingFrequencyEvidence() throws Exception {
    Path repositoryPath = temporaryDirectory.resolve("binary-repository");
    DemoRepositoryGenerator.generate(repositoryPath);

    Path assets = repositoryPath.resolve("assets");
    Files.createDirectories(assets);
    Files.write(assets.resolve("logo.bin"), new byte[] {0, 1, 2, 3});
    Files.writeString(assets.resolve("mixed.dat"), "text version\n");
    try (Git git = Git.open(repositoryPath.toFile())) {
      git.add().addFilepattern("assets/logo.bin").addFilepattern("assets/mixed.dat").call();
      commit(git, "Add binary and text assets", "2025-01-15T09:00:00Z");

      Files.write(assets.resolve("logo.bin"), new byte[] {0, 4, 5, 6});
      Files.write(assets.resolve("mixed.dat"), new byte[] {0, 7, 8, 9});
      git.add().addFilepattern("assets/logo.bin").addFilepattern("assets/mixed.dat").call();
      commit(git, "Update asset content", "2025-01-16T09:00:00Z");
    }

    GitChangeFrequencyAnalyzer analyzer = new GitChangeFrequencyAnalyzer();
    RepositoryAnalysis analysis =
        analyzer.analyze(new AnalysisRequest(repositoryPath.toString(), "main"));

    FileChangeFrequency binary = file(analysis, "assets/logo.bin");
    assertThat(binary.commitCount()).isEqualTo(2);
    assertThat(binary.binaryChangeCount()).isEqualTo(2);
    assertThat(binary.lineMetricAvailability()).isEqualTo(LineMetricAvailability.UNAVAILABLE);
    assertThat(binary.commits()).hasSize(2);

    FileChangeFrequency mixed = file(analysis, "assets/mixed.dat");
    assertThat(mixed.commitCount()).isEqualTo(2);
    assertThat(mixed.binaryChangeCount()).isEqualTo(1);
    assertThat(mixed.lineMetricAvailability()).isEqualTo(LineMetricAvailability.PARTIAL);

    assertThat(analysis.warnings())
        .filteredOn(warning -> warning.code() == AnalysisWarningCode.BINARY_CONTENT)
        .singleElement()
        .satisfies(
            warning -> {
              assertThat(warning.category()).isEqualTo(AnalysisWarningCategory.DATA_QUALITY);
              assertThat(warning.severity()).isEqualTo(AnalysisWarningSeverity.WARNING);
              assertThat(warning.occurrenceCount()).isEqualTo(3);
              assertThat(warning.message())
                  .contains("included in change frequency", "churn are unavailable");
            });

    RepositoryAnalysis excludedBinaryChanges =
        analyzer.analyze(
            new AnalysisRequest(
                repositoryPath.toString(), "main", null, null, List.of("assets/**")));
    assertThat(excludedBinaryChanges.hotspots())
        .extracting(FileChangeFrequency::path)
        .doesNotContain("assets/logo.bin", "assets/mixed.dat");
    assertThat(excludedBinaryChanges.warnings())
        .extracting(AnalysisWarning::code)
        .doesNotContain(AnalysisWarningCode.BINARY_CONTENT);
  }

  @Test
  void preservesRenameAndDeletionEvidenceWhenAPathIsLaterRecreated() throws Exception {
    Path repositoryPath = temporaryDirectory.resolve("deleted-repository");
    generateDeletionHistory(repositoryPath);

    RepositoryAnalysis analysis =
        new GitChangeFrequencyAnalyzer()
            .analyze(new AnalysisRequest(repositoryPath.toString(), "main"));

    List<FileChangeFrequency> identitiesAtCurrentPath =
        analysis.hotspots().stream()
            .filter(hotspot -> hotspot.path().equals("src/Current.java"))
            .toList();
    assertThat(identitiesAtCurrentPath).hasSize(2);

    FileChangeFrequency deletedIdentity =
        identitiesAtCurrentPath.stream()
            .filter(FileChangeFrequency::deleted)
            .findFirst()
            .orElseThrow();
    FileChangeFrequency activeReplacement =
        identitiesAtCurrentPath.stream()
            .filter(hotspot -> !hotspot.deleted())
            .findFirst()
            .orElseThrow();

    assertThat(deletedIdentity.historicalPaths())
        .containsExactly("src/Legacy.java", "src/Current.java");
    assertThat(deletedIdentity.commitCount()).isEqualTo(5);
    assertThat(deletedIdentity.commits())
        .extracting(CommitEvidence::message)
        .contains("Delete current component");
    assertThat(deletedIdentity.lineMetricAvailability())
        .isEqualTo(LineMetricAvailability.AVAILABLE);
    assertThat(activeReplacement.historicalPaths()).containsExactly("src/Current.java");
    assertThat(activeReplacement.commitCount()).isEqualTo(1);
    assertThat(activeReplacement.fileIdentity()).isNotEqualTo(deletedIdentity.fileIdentity());

    assertThat(analysis.warnings())
        .singleElement()
        .satisfies(
            warning -> {
              assertThat(warning.code()).isEqualTo(AnalysisWarningCode.DELETED_FILES_AT_BRANCH_TIP);
              assertThat(warning.category()).isEqualTo(AnalysisWarningCategory.DATA_QUALITY);
              assertThat(warning.severity()).isEqualTo(AnalysisWarningSeverity.INFO);
              assertThat(warning.occurrenceCount()).isEqualTo(1);
              assertThat(warning.message())
                  .contains("absent at the selected branch tip", "historical metrics remain valid");
            });
  }

  @Test
  void reportsBranchTipDeletionWhenTheDeletionIsOutsideTheDateRange() throws Exception {
    Path repositoryPath = temporaryDirectory.resolve("date-filtered-deletion-repository");
    generateDeletionHistory(repositoryPath);

    RepositoryAnalysis analysis =
        new GitChangeFrequencyAnalyzer()
            .analyze(
                new AnalysisRequest(
                    repositoryPath.toString(),
                    "main",
                    Instant.parse("2025-02-01T00:00:00Z"),
                    Instant.parse("2025-02-05T00:00:00Z"),
                    List.of()));

    assertThat(analysis.hotspots())
        .singleElement()
        .satisfies(
            hotspot -> {
              assertThat(hotspot.path()).isEqualTo("src/Current.java");
              assertThat(hotspot.deleted()).isTrue();
              assertThat(hotspot.commitCount()).isEqualTo(4);
              assertThat(hotspot.commits())
                  .extracting(CommitEvidence::message)
                  .doesNotContain("Delete current component", "Recreate current path");
            });
    assertThat(analysis.scope().dateExcludedCommitCount()).isEqualTo(2);
    assertThat(analysis.warnings())
        .extracting(AnalysisWarning::code)
        .containsExactly(
            AnalysisWarningCode.DELETED_FILES_AT_BRANCH_TIP,
            AnalysisWarningCode.DATE_RANGE_APPLIED);
  }

  @Test
  void omitsFullyExcludedDeletedIdentitiesAndTheirDeletionNotice() throws Exception {
    Path repositoryPath = temporaryDirectory.resolve("excluded-deletion-repository");
    generateDeletionHistory(repositoryPath);

    RepositoryAnalysis analysis =
        new GitChangeFrequencyAnalyzer()
            .analyze(
                new AnalysisRequest(
                    repositoryPath.toString(), "main", null, null, List.of("src/**")));

    assertThat(analysis.hotspots()).isEmpty();
    assertThat(analysis.scope().pathExcludedFileChangeCount()).isEqualTo(6);
    assertThat(analysis.warnings())
        .extracting(AnalysisWarning::code)
        .containsExactly(AnalysisWarningCode.PATHS_EXCLUDED)
        .doesNotContain(AnalysisWarningCode.DELETED_FILES_AT_BRANCH_TIP);
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

  private static FileChangeFrequency file(RepositoryAnalysis analysis, String path) {
    return analysis.hotspots().stream()
        .filter(hotspot -> hotspot.path().equals(path))
        .findFirst()
        .orElseThrow();
  }

  private static void commit(Git git, String message, String timestamp) throws Exception {
    PersonIdent identity =
        new PersonIdent(
            "Fixture Author", "fixture@example.test", Instant.parse(timestamp), ZoneOffset.UTC);
    git.commit().setMessage(message).setAuthor(identity).setCommitter(identity).call();
  }

  private static void generateDeletionHistory(Path repositoryPath) throws Exception {
    Files.createDirectories(repositoryPath);
    try (Git git =
        Git.init().setDirectory(repositoryPath.toFile()).setInitialBranch("main").call()) {
      write(repositoryPath, "src/Legacy.java", "final class Legacy { int value = 1; }\n");
      git.add().addFilepattern("src/Legacy.java").call();
      commit(git, "Add legacy component", "2025-02-01T09:00:00Z");

      write(repositoryPath, "src/Legacy.java", "final class Legacy { int value = 2; }\n");
      git.add().addFilepattern("src/Legacy.java").call();
      commit(git, "Update legacy component", "2025-02-02T09:00:00Z");

      String renamedContent = Files.readString(repositoryPath.resolve("src/Legacy.java"));
      git.rm().addFilepattern("src/Legacy.java").call();
      write(repositoryPath, "src/Current.java", renamedContent);
      git.add().addFilepattern("src/Current.java").call();
      commit(git, "Rename legacy component", "2025-02-03T09:00:00Z");

      write(repositoryPath, "src/Current.java", "final class Current { int value = 3; }\n");
      git.add().addFilepattern("src/Current.java").call();
      commit(git, "Update current component", "2025-02-04T09:00:00Z");

      git.rm().addFilepattern("src/Current.java").call();
      commit(git, "Delete current component", "2025-02-05T09:00:00Z");

      write(repositoryPath, "src/Current.java", "final class Replacement {}\n");
      git.add().addFilepattern("src/Current.java").call();
      commit(git, "Recreate current path", "2025-02-06T09:00:00Z");
    }
  }

  private static void write(Path repositoryPath, String relativePath, String content)
      throws Exception {
    Path file = repositoryPath.resolve(relativePath.replace('/', java.io.File.separatorChar));
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
