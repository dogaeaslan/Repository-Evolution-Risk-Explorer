package dev.riskexplorer.analysis;

import java.time.Instant;

public record CommitEvidence(
    String commitId, Instant authoredAt, String authorName, String message) {}
