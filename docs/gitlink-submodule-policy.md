# Gitlink and submodule ingestion policy

Status: domain contract, Gitlink detection, and lifecycle fixtures implemented; API assertions and dashboard presentation remain.

A Git tree entry with mode `160000` is a Gitlink: an opaque pointer to a commit in another repository. For the MVP, Repository Evolution Risk Explorer analyzes changes to that pointer as part of the selected containing repository. It does not initialize, clone, enter, or execute the referenced submodule.

The containing-repository commit remains valid change-frequency evidence for the Gitlink path. Line additions, deletions, and churn are unavailable because the Gitlink has no ordinary file blob to compare. Path exclusions apply to the Gitlink path in the same way as other repository-relative paths, and deletion continues to describe whether that path is present at the selected branch tip.

When a path changes between an ordinary file and a Gitlink, JGit presents the mode transition as a deletion and an addition. The analyzer retains separate file identities at that path: the former identity is deleted and the new identity starts with the mode-changing commit. Both events can count toward path exclusions; only the Gitlink side contributes to the Gitlink limitation count.

File observations keep reason-specific binary and Gitlink change counts, plus a general count of changes for which line metrics are unavailable. `LineMetricAvailability` is derived from that general count so future unavailable-diff reasons can participate without being mislabeled as binary or Gitlink content. Gitlink limitations use the `UNSUPPORTED_CONTENT` warning category and the `GITLINK_CONTENT` warning code.
