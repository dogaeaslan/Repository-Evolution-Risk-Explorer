package dev.riskexplorer.analysis;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.stereotype.Component;

@Component
public class GitChangeFrequencyAnalyzer {

  private static final int RENAME_SIMILARITY_PERCENT = 60;

  public RepositoryAnalysis analyze(AnalysisRequest request) {
    String branch = validateBranch(request.branch());
    Path requestedPath = validateRepositoryPath(request.repositoryPath());
    validateDateRange(request.fromInclusive(), request.toExclusive());
    GitPathExclusions exclusions = new GitPathExclusions(request.exclusionPatterns());

    try (Repository repository = openRepository(requestedPath)) {
      Ref branchRef = repository.exactRef(Constants.R_HEADS + branch);
      if (branchRef == null) {
        throw new AnalysisException(
            "Branch '" + branch + "' does not exist in the selected repository.");
      }

      return analyzeHistory(repository, branchRef, branch, request, exclusions);
    } catch (AnalysisException exception) {
      throw exception;
    } catch (IOException exception) {
      throw new AnalysisException("The Git repository could not be read.", exception);
    }
  }

  private RepositoryAnalysis analyzeHistory(
      Repository repository,
      Ref branchRef,
      String branch,
      AnalysisRequest request,
      GitPathExclusions exclusions)
      throws IOException {
    Map<String, FileAccumulator> activeFiles = new LinkedHashMap<>();
    List<FileAccumulator> allFiles = new ArrayList<>();
    int traversedCommitCount = 0;
    int analyzedCommitCount = 0;
    int skippedMergeCount = 0;
    int dateExcludedCommitCount = 0;
    int pathExcludedFileChangeCount = 0;
    Instant periodStart = null;
    Instant periodEnd = null;

    try (RevWalk walk = new RevWalk(repository);
        ObjectReader reader = repository.newObjectReader();
        DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
      walk.sort(RevSort.TOPO);
      walk.sort(RevSort.REVERSE, true);
      walk.markStart(walk.parseCommit(branchRef.getObjectId()));

      formatter.setRepository(repository);
      formatter.setDetectRenames(true);
      formatter.getRenameDetector().setRenameScore(RENAME_SIMILARITY_PERCENT);

      for (RevCommit commit : walk) {
        traversedCommitCount++;
        Instant authoredAt = commit.getAuthorIdent().getWhenAsInstant();

        if (commit.getParentCount() > 1) {
          skippedMergeCount++;
          continue;
        }

        boolean dateEligible =
            isDateEligible(authoredAt, request.fromInclusive(), request.toExclusive());
        CommitEvidence evidence = null;
        if (dateEligible) {
          analyzedCommitCount++;
          periodStart =
              periodStart == null || authoredAt.isBefore(periodStart) ? authoredAt : periodStart;
          periodEnd = periodEnd == null || authoredAt.isAfter(periodEnd) ? authoredAt : periodEnd;
          evidence =
              new CommitEvidence(
                  commit.getName(),
                  authoredAt,
                  commit.getAuthorIdent().getName(),
                  commit.getShortMessage());
        } else {
          dateExcludedCommitCount++;
        }

        AbstractTreeIterator oldTree = oldTree(reader, walk, commit);
        AbstractTreeIterator newTree = treeIterator(reader, commit.getTree());
        for (DiffEntry entry : formatter.scan(oldTree, newTree)) {
          pathExcludedFileChangeCount +=
              recordChange(entry, commit.getName(), evidence, exclusions, activeFiles, allFiles);
        }
      }
    }

    List<AnalysisWarning> warnings = new ArrayList<>();
    if (skippedMergeCount > 0) {
      warnings.add(
          new AnalysisWarning(
              "MERGE_DIFFS_EXCLUDED",
              skippedMergeCount
                  + " merge commit diff(s) were excluded to avoid double-counting changes; reachable ordinary commits were analyzed individually."));
    }
    if (dateExcludedCommitCount > 0) {
      warnings.add(
          new AnalysisWarning(
              "DATE_RANGE_APPLIED",
              dateExcludedCommitCount
                  + " ordinary commit(s) outside the requested date range were traversed for file identity but excluded from metrics."));
    }
    if (pathExcludedFileChangeCount > 0) {
      warnings.add(
          new AnalysisWarning(
              "PATHS_EXCLUDED",
              pathExcludedFileChangeCount
                  + " in-range file change(s) matched the configured Git-path exclusions and were omitted from metrics."));
    }

    List<FileChangeFrequency> hotspots =
        allFiles.stream()
            .map(FileAccumulator::toObservation)
            .filter(observation -> observation.commitCount() > 0)
            .sorted(
                Comparator.comparingInt(FileChangeFrequency::commitCount)
                    .reversed()
                    .thenComparing(FileChangeFrequency::path))
            .toList();

    return new RepositoryAnalysis(
        UUID.randomUUID().toString(),
        repository.getWorkTree().toPath().toRealPath().toString(),
        branch,
        periodStart,
        periodEnd,
        traversedCommitCount,
        analyzedCommitCount,
        new AnalysisScope(
            request.fromInclusive(),
            request.toExclusive(),
            exclusions.patterns(),
            MergePolicy.EXCLUDE_MERGE_DIFFS,
            dateExcludedCommitCount,
            pathExcludedFileChangeCount),
        hotspots,
        warnings);
  }

  private static int recordChange(
      DiffEntry entry,
      String commitId,
      CommitEvidence evidence,
      GitPathExclusions exclusions,
      Map<String, FileAccumulator> activeFiles,
      List<FileAccumulator> allFiles) {
    boolean pathExcluded = evidence != null && exclusions.matches(eligibilityPath(entry));
    CommitEvidence eligibleEvidence = pathExcluded ? null : evidence;
    switch (entry.getChangeType()) {
      case ADD ->
          newFile(entry.getNewPath(), commitId, activeFiles, allFiles).record(eligibleEvidence);
      case COPY ->
          newFile(entry.getNewPath(), commitId, activeFiles, allFiles).record(eligibleEvidence);
      case MODIFY ->
          currentFile(entry.getNewPath(), commitId, activeFiles, allFiles).record(eligibleEvidence);
      case DELETE -> {
        FileAccumulator deleted = currentFile(entry.getOldPath(), commitId, activeFiles, allFiles);
        deleted.record(eligibleEvidence);
        deleted.markDeleted();
        activeFiles.remove(entry.getOldPath());
      }
      case RENAME -> {
        FileAccumulator renamed = currentFile(entry.getOldPath(), commitId, activeFiles, allFiles);
        renamed.record(eligibleEvidence);
        activeFiles.remove(entry.getOldPath());
        renamed.renameTo(entry.getNewPath());
        activeFiles.put(entry.getNewPath(), renamed);
      }
    }
    return pathExcluded ? 1 : 0;
  }

  private static String eligibilityPath(DiffEntry entry) {
    return entry.getChangeType() == DiffEntry.ChangeType.DELETE
        ? entry.getOldPath()
        : entry.getNewPath();
  }

  private static FileAccumulator newFile(
      String path,
      String firstCommitId,
      Map<String, FileAccumulator> activeFiles,
      List<FileAccumulator> allFiles) {
    FileAccumulator accumulator = new FileAccumulator(path, firstCommitId);
    activeFiles.put(path, accumulator);
    allFiles.add(accumulator);
    return accumulator;
  }

  private static FileAccumulator currentFile(
      String path,
      String firstCommitId,
      Map<String, FileAccumulator> activeFiles,
      List<FileAccumulator> allFiles) {
    FileAccumulator existing = activeFiles.get(path);
    return existing != null ? existing : newFile(path, firstCommitId, activeFiles, allFiles);
  }

  private static AbstractTreeIterator oldTree(ObjectReader reader, RevWalk walk, RevCommit commit)
      throws IOException {
    if (commit.getParentCount() == 0) {
      return new EmptyTreeIterator();
    }
    RevTree parentTree = walk.parseCommit(commit.getParent(0)).getTree();
    return treeIterator(reader, parentTree);
  }

  private static CanonicalTreeParser treeIterator(ObjectReader reader, RevTree tree)
      throws IOException {
    CanonicalTreeParser parser = new CanonicalTreeParser();
    parser.reset(reader, tree.getId());
    return parser;
  }

  private static Repository openRepository(Path requestedPath) throws IOException {
    Repository repository =
        new FileRepositoryBuilder().findGitDir(requestedPath.toFile()).setMustExist(true).build();
    if (repository.isBare()) {
      repository.close();
      throw new AnalysisException("Bare repositories are not supported by the MVP.");
    }
    return repository;
  }

  private static Path validateRepositoryPath(String repositoryPath) {
    if (repositoryPath == null || repositoryPath.isBlank()) {
      throw new AnalysisException("Select a local Git repository before starting analysis.");
    }
    try {
      Path path = Path.of(repositoryPath.trim());
      Path absolute =
          path.isAbsolute()
              ? path.normalize()
              : Path.of("").toAbsolutePath().resolve(path).normalize();
      if (!Files.isDirectory(absolute)) {
        throw new AnalysisException(
            "Repository path '" + repositoryPath + "' is not an existing directory.");
      }
      return absolute;
    } catch (InvalidPathException exception) {
      throw new AnalysisException("The repository path is invalid.", exception);
    }
  }

  private static String validateBranch(String branch) {
    if (branch == null || branch.isBlank()) {
      throw new AnalysisException("Select a branch before starting analysis.");
    }
    String trimmed = branch.trim();
    if (trimmed.length() > 255 || !Repository.isValidRefName(Constants.R_HEADS + trimmed)) {
      throw new AnalysisException("Branch name '" + trimmed + "' is invalid.");
    }
    return trimmed;
  }

  private static void validateDateRange(Instant fromInclusive, Instant toExclusive) {
    if (fromInclusive != null && toExclusive != null && !fromInclusive.isBefore(toExclusive)) {
      throw new AnalysisException(
          "The analysis start must be earlier than the exclusive end instant.");
    }
  }

  private static boolean isDateEligible(
      Instant authoredAt, Instant fromInclusive, Instant toExclusive) {
    return (fromInclusive == null || !authoredAt.isBefore(fromInclusive))
        && (toExclusive == null || authoredAt.isBefore(toExclusive));
  }

  private static final class FileAccumulator {

    private final String fileIdentity;
    private final List<String> historicalPaths = new ArrayList<>();
    private final List<CommitEvidence> commits = new ArrayList<>();
    private final Set<String> recordedCommitIds = new LinkedHashSet<>();
    private String currentPath;
    private boolean deleted;

    private FileAccumulator(String initialPath, String firstCommitId) {
      fileIdentity =
          UUID.nameUUIDFromBytes((initialPath + '\0' + firstCommitId).getBytes(UTF_8)).toString();
      currentPath = initialPath;
      historicalPaths.add(initialPath);
    }

    private void record(CommitEvidence evidence) {
      if (evidence != null && recordedCommitIds.add(evidence.commitId())) {
        commits.add(evidence);
      }
    }

    private void renameTo(String newPath) {
      currentPath = newPath;
      deleted = false;
      if (!historicalPaths.contains(newPath)) {
        historicalPaths.add(newPath);
      }
    }

    private void markDeleted() {
      deleted = true;
    }

    private FileChangeFrequency toObservation() {
      List<CommitEvidence> newestFirst =
          commits.stream()
              .sorted(
                  Comparator.comparing(CommitEvidence::authoredAt)
                      .reversed()
                      .thenComparing(CommitEvidence::commitId))
              .toList();
      return new FileChangeFrequency(
          fileIdentity, currentPath, historicalPaths, deleted, newestFirst.size(), newestFirst);
    }
  }
}
