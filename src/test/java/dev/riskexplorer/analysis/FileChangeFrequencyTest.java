package dev.riskexplorer.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class FileChangeFrequencyTest {

  @Test
  void representsDistinctBinaryAndGitlinkReasonsWithinGenericLineMetricAvailability() {
    FileChangeFrequency observation =
        new FileChangeFrequency(
            "file-1",
            "vendor/component",
            List.of("vendor/component"),
            false,
            3,
            1,
            1,
            2,
            LineMetricAvailability.PARTIAL,
            List.of());

    assertThat(observation.binaryChangeCount()).isEqualTo(1);
    assertThat(observation.gitlinkChangeCount()).isEqualTo(1);
    assertThat(observation.unavailableLineMetricChangeCount()).isEqualTo(2);
    assertThat(observation.lineMetricAvailability()).isEqualTo(LineMetricAvailability.PARTIAL);
  }

  @Test
  void rejectsReasonCountsThatExceedUnavailableLineMetrics() {
    assertThatThrownBy(
            () ->
                new FileChangeFrequency(
                    "file-1",
                    "vendor/component",
                    List.of("vendor/component"),
                    false,
                    2,
                    1,
                    1,
                    1,
                    LineMetricAvailability.PARTIAL,
                    List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Reason-specific limitation counts");
  }
}
