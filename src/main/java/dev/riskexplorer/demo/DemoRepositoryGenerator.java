package dev.riskexplorer.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

public final class DemoRepositoryGenerator {

  private static final String MARKER_FILE = "risk-explorer-demo-marker";
  private static final PersonIdent ALICE =
      person("Alice Example", "alice@example.test", "2025-01-01T09:00:00Z");
  private static final PersonIdent BOB =
      person("Bob Example", "bob@example.test", "2025-01-01T09:00:00Z");
  private static final PersonIdent CAROL =
      person("Carol Example", "carol@example.test", "2025-01-01T09:00:00Z");

  private DemoRepositoryGenerator() {}

  public static void main(String[] args) throws Exception {
    Path target = args.length == 0 ? Path.of("demo-repository") : Path.of(args[0]);
    DemoRepositorySummary summary = generate(target);
    System.out.println("Created deterministic demo repository: " + summary.path());
    System.out.println("Branch: " + summary.branch());
    System.out.println("Commits reachable from branch: " + summary.commitCount());
  }

  public static DemoRepositorySummary generate(Path target) throws IOException, GitAPIException {
    Path normalized = target.toAbsolutePath().normalize();
    prepareTarget(normalized);

    try (Git git = Git.init().setDirectory(normalized.toFile()).setInitialBranch("main").call()) {
      Repository repository = git.getRepository();
      Files.writeString(repository.getDirectory().toPath().resolve(MARKER_FILE), "generated\n");

      write(normalized, "README.md", "# Stable demo component\n");
      write(normalized, "src/HighChurn.java", "final class HighChurn { int value = 0; }\n");
      write(normalized, "src/Ownership.java", "final class Ownership { int owner = 1; }\n");
      write(normalized, "src/PairA.java", "final class PairA { int version = 1; }\n");
      write(normalized, "src/PairB.java", "final class PairB { int version = 1; }\n");
      write(normalized, "src/LegacyName.java", "final class RenamedComponent {}\n");
      write(normalized, "generated/ApiClient.java", "// generated\nfinal class ApiClient {}\n");
      stage(git, ".");
      commit(git, "Initial stable project", ALICE, "2025-01-01T09:00:00Z");

      write(normalized, "src/HighChurn.java", "final class HighChurn { int value = 1; }\n");
      stage(git, "src/HighChurn.java");
      commit(git, "Evolve parser state", ALICE, "2025-01-02T09:00:00Z");

      write(normalized, "src/HighChurn.java", "final class HighChurn { int value = 2; }\n");
      write(normalized, "src/PairA.java", "final class PairA { int version = 2; }\n");
      write(normalized, "src/PairB.java", "final class PairB { int version = 2; }\n");
      stage(git, "src/HighChurn.java", "src/PairA.java", "src/PairB.java");
      commit(git, "Adjust parser and paired components", BOB, "2025-01-03T09:00:00Z");

      write(normalized, "src/Ownership.java", "final class Ownership { int owner = 2; }\n");
      stage(git, "src/Ownership.java");
      commit(git, "Refine ownership component", ALICE, "2025-01-04T09:00:00Z");

      write(normalized, "src/HighChurn.java", "final class HighChurn { int value = 3; }\n");
      stage(git, "src/HighChurn.java");
      commit(git, "Continue parser redesign", ALICE, "2025-01-05T09:00:00Z");

      write(normalized, "src/PairA.java", "final class PairA { int version = 3; }\n");
      write(normalized, "src/PairB.java", "final class PairB { int version = 3; }\n");
      stage(git, "src/PairA.java", "src/PairB.java");
      commit(git, "Keep paired components aligned", BOB, "2025-01-06T09:00:00Z");

      write(normalized, "src/Ownership.java", "final class Ownership { int owner = 3; }\n");
      stage(git, "src/Ownership.java");
      commit(git, "Extend ownership component", ALICE, "2025-01-07T09:00:00Z");

      write(normalized, "src/HighChurn.java", "final class HighChurn { int value = 4; }\n");
      stage(git, "src/HighChurn.java");
      commit(git, "fix: correct parser edge case", CAROL, "2025-01-08T09:00:00Z");

      String renamedContent = Files.readString(normalized.resolve("src/LegacyName.java"));
      git.rm().addFilepattern("src/LegacyName.java").call();
      write(normalized, "src/RenamedComponent.java", renamedContent);
      stage(git, "src/RenamedComponent.java");
      commit(git, "Rename legacy component", ALICE, "2025-01-09T09:00:00Z");

      write(normalized, "src/Ownership.java", "final class Ownership { int owner = 4; }\n");
      stage(git, "src/Ownership.java");
      commit(git, "BUG-42 repair ownership fallback", ALICE, "2025-01-10T09:00:00Z");

      git.branchCreate().setName("feature/reporting").call();
      git.checkout().setName("feature/reporting").call();
      write(normalized, "src/FeatureReport.java", "final class FeatureReport {}\n");
      stage(git, "src/FeatureReport.java");
      RevCommit featureTip = commit(git, "Add reporting branch work", BOB, "2025-01-11T09:00:00Z");

      git.checkout().setName("main").call();
      write(normalized, "docs/main-note.md", "Main branch documentation.\n");
      stage(git, "docs/main-note.md");
      RevCommit mainTip = commit(git, "Document main branch", ALICE, "2025-01-12T09:00:00Z");

      write(normalized, "src/FeatureReport.java", "final class FeatureReport {}\n");
      stage(git, "src/FeatureReport.java");
      createMergeCommit(
          repository,
          mainTip,
          featureTip,
          ALICE,
          "2025-01-13T09:00:00Z",
          "Merge feature/reporting");

      write(normalized, "src/HighChurn.java", "final class HighChurn { int value = 5; }\n");
      stage(git, "src/HighChurn.java");
      commit(git, "hotfix: stabilize parser recovery", CAROL, "2025-01-14T09:00:00Z");

      int commitCount = 0;
      for (RevCommit ignored : git.log().add(repository.resolve("refs/heads/main")).call()) {
        commitCount++;
      }
      return new DemoRepositorySummary(normalized, "main", commitCount);
    }
  }

  private static void createMergeCommit(
      Repository repository,
      RevCommit mainTip,
      RevCommit featureTip,
      PersonIdent identity,
      String timestamp,
      String message)
      throws IOException {
    PersonIdent datedIdentity = at(identity, timestamp);
    DirCache index = repository.readDirCache();

    ObjectId mergeCommitId;
    try (ObjectInserter inserter = repository.newObjectInserter()) {
      CommitBuilder builder = new CommitBuilder();
      builder.setTreeId(index.writeTree(inserter));
      builder.setParentIds(mainTip, featureTip);
      builder.setAuthor(datedIdentity);
      builder.setCommitter(datedIdentity);
      builder.setMessage(message);
      mergeCommitId = inserter.insert(builder);
      inserter.flush();
    }

    RefUpdate update = repository.updateRef(Constants.R_HEADS + "main");
    update.setExpectedOldObjectId(mainTip);
    update.setNewObjectId(mergeCommitId);
    update.setRefLogMessage("merge feature/reporting", false);
    RefUpdate.Result result = update.update();
    if (result != RefUpdate.Result.FAST_FORWARD && result != RefUpdate.Result.NEW) {
      throw new IOException("Could not create deterministic merge commit: " + result);
    }
  }

  private static RevCommit commit(Git git, String message, PersonIdent identity, String timestamp)
      throws GitAPIException {
    PersonIdent datedIdentity = at(identity, timestamp);
    return git.commit()
        .setMessage(message)
        .setAuthor(datedIdentity)
        .setCommitter(datedIdentity)
        .call();
  }

  private static void stage(Git git, String... paths) throws GitAPIException {
    for (String path : paths) {
      git.add().addFilepattern(path).call();
    }
  }

  private static void write(Path repository, String relativePath, String content)
      throws IOException {
    Path file = repository.resolve(relativePath.replace('/', java.io.File.separatorChar));
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  private static PersonIdent person(String name, String email, String timestamp) {
    return new PersonIdent(name, email, Instant.parse(timestamp), ZoneOffset.UTC);
  }

  private static PersonIdent at(PersonIdent identity, String timestamp) {
    return new PersonIdent(
        identity.getName(), identity.getEmailAddress(), Instant.parse(timestamp), ZoneOffset.UTC);
  }

  private static void prepareTarget(Path target) throws IOException {
    Path workingDirectory = Path.of("").toAbsolutePath().normalize();
    if (target.getParent() == null || target.equals(workingDirectory)) {
      throw new IOException("Refusing to replace a broad or unsafe demo-repository path.");
    }
    if (!Files.exists(target)) {
      Files.createDirectories(target);
      return;
    }

    boolean empty;
    try (var children = Files.list(target)) {
      empty = children.findAny().isEmpty();
    }
    Path marker = target.resolve(".git").resolve(MARKER_FILE);
    if (!empty && !Files.isRegularFile(marker)) {
      throw new IOException(
          "Refusing to replace an existing directory that was not generated by this project: "
              + target);
    }

    if (!empty) {
      deleteRecursively(target);
      Files.createDirectories(target);
    }
  }

  private static void deleteRecursively(Path target) throws IOException {
    try (var paths = Files.walk(target)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        if (!Files.isDirectory(path)) {
          path.toFile().setWritable(true);
        }
        Files.delete(path);
      }
    }
  }

  public record DemoRepositorySummary(Path path, String branch, int commitCount) {

    public DemoRepositorySummary {
      path = path.toAbsolutePath().normalize();
    }
  }
}
