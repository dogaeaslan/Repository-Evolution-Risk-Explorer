package dev.riskexplorer.demo;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemoRepositoryGeneratorTest {

  @TempDir Path temporaryDirectory;

  @Test
  void refusesToReplaceAnUnmarkedDirectory() throws Exception {
    Path existingDirectory = temporaryDirectory.resolve("existing");
    Files.createDirectories(existingDirectory);
    Files.writeString(existingDirectory.resolve("important.txt"), "preserve me");

    assertThatThrownBy(() -> DemoRepositoryGenerator.generate(existingDirectory))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Refusing to replace");
  }
}
