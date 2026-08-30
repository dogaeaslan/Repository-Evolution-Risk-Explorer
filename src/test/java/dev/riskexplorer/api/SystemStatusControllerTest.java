package dev.riskexplorer.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SystemStatusControllerTest {

  @Test
  void reportsFoundationStatus() {
    SystemStatus status = new SystemStatusController().status();

    assertThat(status.application()).isEqualTo("Repository Evolution Risk Explorer");
    assertThat(status.status()).isEqualTo("foundation-ready");
  }
}
