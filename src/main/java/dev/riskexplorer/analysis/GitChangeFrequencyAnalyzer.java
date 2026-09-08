package dev.riskexplorer.analysis;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
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
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
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
    int shallowBoundaryCount = 0;
    int dateExcludedCommitCount = 0;
    int pathExcludedFileChangeCount = 0;
    Instant periodStart = null;
    Instant periodEnd = null;
    Set<ObjectId> shallowCommits = repository.getObjectDatabase().getShallowCommits();

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

        if (shallowCommits.contains(commit.getId())) {
          shallowBoundaryCount++;
          continue;
        }

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
              recordChange(
                  reader, entry, commit.getName(), evidence, exclusions, activeFiles, allFiles);
        }
      }
    }

    int binaryChangeCount = allFiles.stream().mapToInt(FileAccumulator::binaryChangeCount).sum();
    List<AnalysisWarning> warnings = new ArrayList<>();
    if (shallowBoundaryCount > 0) {
      warnings.add(
          new AnalysisWarning(
              AnalysisWarningCode.SHALLOW_HISTORY,
              shallowBoundaryCount,
              "History for the selected branch stops at "
                  + shallowBoundaryCount
                  + " shallow boundary commit(s). Results include only changes with available parent history. Fetch the full repository history and analyze again."));
    }
    if (binaryChangeCount > 0) {
      warnings.add(
          new AnalysisWarning(
              AnalysisWarningCode.BINARY_CONTENT,
              binaryChangeCount,
              binaryChangeCount
                  + " in-scope binary file change(s) were included in change frequency. Line additions, deletions, and churn are unavailable for those changes."));
    }
    if (skippedMergeCount > 0) {
      warnings.add(
          new AnalysisWarning(
              AnalysisWarningCode.MERGE_DIFFS_EXCLUDED,
              skippedMergeCount,
              skippedMergeCount
                  + " merge commit diff(s) were excluded to avoid double-counting changes; reachable ordinary commits were analyzed individually."));
    }
    if (dateExcludedCommitCount > 0) {
      warnings.add(
          new AnalysisWarning(
              AnalysisWarningCode.DATE_RANGE_APPLIED,
              dateExcludedCommitCount,
              dateExcludedCommitCount
                  + " ordinary commit(s) outside the requested date range were traversed for file identity but excluded from metrics."));
    }
    if (pathExcludedFileChangeCount > 0) {
      warnings.add(
          new AnalysisWarning(
              AnalysisWarningCode.PATHS_EXCLUDED,
              pathExcludedFileChangeCount,
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
      ObjectReader reader,
      DiffEntry entry,
      String commitId,
      CommitEvidence evidence,
      GitPathExclusions exclusions,
      Map<String, FileAccumulator> activeFiles,
      List<FileAccumulator> allFiles)
      throws IOException {
    boolean pathExcluded = evidence != null && exclusions.matches(eligibilityPath(entry));
    CommitEvidence eligibleEvidence = pathExcluded ? null : evidence;
    boolean binaryChange = eligibleEvidence != null && isBinaryChange(reader, entry);
    switch (entry.getChangeType()) {
      case ADD ->
          newFile(entry.getNewPath(), commitId, activeFiles, allFiles)
              .record(eligibleEvidence, binaryChange);
      case COPY ->
          newFile(entry.getNewPath(), commitId, activeFiles, allFiles)
              .record(eligibleEvidence, binaryChange);
      case MODIFY ->
          currentFile(entry.getNewPath(), commitId, activeFiles, allFiles)
              .record(eligibleEvidence, binaryChange);
      case DELETE -> {
        FileAccumulator deleted = currentFile(entry.getOldPath(), commitId, activeFiles, allFiles);
        deleted.record(eligibleEvidence, binaryChange);
        deleted.markDeleted();
        activeFiles.remove(entry.getOldPath());
      }
      case RENAME -> {
        FileAccumulator renamed = currentFile(entry.getOldPath(), commitId, activeFiles, allFiles);
        renamed.record(eligibleEvidence, binaryChange);
        activeFiles.remove(entry.getOldPath());
        renamed.renameTo(entry.getNewPath());
        activeFiles.put(entry.getNewPath(), renamed);
      }
    }
    return pathExcluded ? 1 : 0;
  }

  private static boolean isBinaryChange(ObjectReader reader, DiffEntry entry) throws IOException {
    return isBinaryBlob(reader, entry, DiffEntry.Side.OLD)
        || isBinaryBlob(reader, entry, DiffEntry.Side.NEW);
  }

  private static boolean isBinaryBlob(ObjectReader reader, DiffEntry entry, DiffEntry.Side side)
      throws IOException {
    if (entry.getMode(side).getObjectType() != Constants.OBJ_BLOB) {
      return false;
    }
    try (InputStream content =
        reader.open(entry.getId(side).toObjectId(), Constants.OBJ_BLOB).openStream()) {
      return RawText.isBinary(content);
    }
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
    private final Set<String> binaryCommitIds = new LinkedHashSet<>();
    private String currentPath;
    private boolean deleted;

    private FileAccumulator(String initialPath, String firstCommitId) {
      fileIdentity =
          UUID.nameUUIDFromBytes((initialPath + '\0' + firstCommitId).getBytes(UTF_8)).toString();
      currentPath = initialPath;
      historicalPaths.add(initialPath);
    }

    private void record(CommitEvidence evidence, boolean binaryChange) {
      if (evidence != null) {
        if (recordedCommitIds.add(evidence.commitId())) {
          commits.add(evidence);
        }
        if (binaryChange) {
          binaryCommitIds.add(evidence.commitId());
        }
      }
    }

    private int binaryChangeCount() {
      return binaryCommitIds.size();
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
          fileIdentity,
          currentPath,
          historicalPaths,
          deleted,
          newestFirst.size(),
          binaryChangeCount(),
          LineMetricAvailability.fromChangeCounts(newestFirst.size(), binaryChangeCount()),
          newestFirst);
    }
  }
}
