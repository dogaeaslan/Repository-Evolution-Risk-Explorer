package dev.riskexplorer.analysis;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class GitPathExclusions {

  private static final int MAX_PATTERN_COUNT = 100;
  private static final int MAX_PATTERN_LENGTH = 512;

  private final List<String> patterns;
  private final List<Pattern> compiledPatterns;

  GitPathExclusions(List<String> requestedPatterns) {
    if (requestedPatterns.size() > MAX_PATTERN_COUNT) {
      throw new AnalysisException(
          "At most " + MAX_PATTERN_COUNT + " exclusion patterns may be configured.");
    }

    Set<String> normalizedPatterns = new LinkedHashSet<>();
    for (String requestedPattern : requestedPatterns) {
      if (requestedPattern == null) {
        throw new AnalysisException("Exclusion patterns must not contain null values.");
      }
      String normalized = normalize(requestedPattern);
      if (!normalized.isEmpty()) {
        normalizedPatterns.add(normalized);
      }
    }

    patterns = List.copyOf(normalizedPatterns);
    compiledPatterns = patterns.stream().map(GitPathExclusions::compile).toList();
  }

  List<String> patterns() {
    return patterns;
  }

  boolean matches(String gitPath) {
    return compiledPatterns.stream().anyMatch(pattern -> pattern.matcher(gitPath).matches());
  }

  private static String normalize(String pattern) {
    String normalized = pattern.trim().replace('\\', '/');
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    if (normalized.length() > MAX_PATTERN_LENGTH) {
      throw new AnalysisException(
          "Each exclusion pattern must be at most " + MAX_PATTERN_LENGTH + " characters.");
    }
    if (normalized.endsWith("/")) {
      normalized += "**";
    }
    return normalized;
  }

  private static Pattern compile(String glob) {
    StringBuilder regex = new StringBuilder("^");
    for (int index = 0; index < glob.length(); index++) {
      char current = glob.charAt(index);
      if (current == '*') {
        boolean doubleStar = index + 1 < glob.length() && glob.charAt(index + 1) == '*';
        if (doubleStar) {
          index++;
          boolean followedBySlash = index + 1 < glob.length() && glob.charAt(index + 1) == '/';
          if (followedBySlash) {
            index++;
            regex.append("(?:.*/)?");
          } else {
            regex.append(".*");
          }
        } else {
          regex.append("[^/]*");
        }
      } else if (current == '?') {
        regex.append("[^/]");
      } else {
        appendLiteral(regex, current);
      }
    }
    return Pattern.compile(regex.append('$').toString());
  }

  private static void appendLiteral(StringBuilder regex, char value) {
    if (".[]{}()+-^$|\\".indexOf(value) >= 0) {
      regex.append('\\');
    }
    regex.append(value);
  }
}
