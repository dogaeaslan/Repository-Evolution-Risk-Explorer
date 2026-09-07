package dev.riskexplorer.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitPathExclusionsTest {

  @Test
  void supportsRecursiveSegmentAndSingleCharacterGlobs() {
    GitPathExclusions exclusions =
        new GitPathExclusions(List.of("generated/", "**/*.min.js", "src/Pair?.java"));

    assertThat(exclusions.matches("generated/ApiClient.java")).isTrue();
    assertThat(exclusions.matches("assets/app.min.js")).isTrue();
    assertThat(exclusions.matches("app.min.js")).isTrue();
    assertThat(exclusions.matches("src/PairA.java")).isTrue();
    assertThat(exclusions.matches("src/nested/PairA.java")).isFalse();
    assertThat(exclusions.matches("src/PairLong.java")).isFalse();
  }

  @Test
  void rejectsAnUnboundedNumberOfPatterns() {
    assertThatThrownBy(() -> new GitPathExclusions(Collections.nCopies(101, "generated/**")))
        .isInstanceOf(AnalysisException.class)
        .hasMessageContaining("At most 100");
  }
}
