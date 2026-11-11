/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MarkdownUtils {
  /**
   * Matches bare URLs which are rendered as links by GFM autolinking. Trailing punctuation is removed from the match
   * afterwards, see {@link #urlEnd(String, int, int)}.
   */
  private static final Pattern URL_PATTERN = Pattern.compile("(?:https?://|www\\.)[^\\s<>\\[\\]]+");

  /** Trailing characters which GFM does not include in an autolink. */
  private static final String URL_TRAILING_PUNCTUATION = "?!.,:;*_~'\"";

  /**
   * Characters with a special meaning in Markdown. Same as {@code MarkdownHelper.markdownEscape()} in the viewer,
   * plus {@code ~} (strikethrough) and {@code =} (setext heading underline).
   */
  private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile("[\\\\`*_{}\\[\\]<>()#+\\-.!|~=]");

  private MarkdownUtils() {}

  /**
   * Escapes plain text so it is rendered literally as Markdown. Bare URLs are left untouched so they are still
   * autolinked (and rendered as images by the viewer). Newlines are not changed.
   *
   * @param text the plain text, may be null
   * @return the escaped text, or null if the text is null
   */
  public static String escapeMarkdown(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    StringBuilder sb = new StringBuilder(text.length() + 16);
    Matcher urlMatcher = URL_PATTERN.matcher(text);
    int start = 0;
    while (urlMatcher.find()) {
      int end = urlEnd(text, urlMatcher.start(), urlMatcher.end());
      sb.append(escapeSpecialChars(text.substring(start, urlMatcher.start())));
      sb.append(text, urlMatcher.start(), end);
      start = end;
    }
    sb.append(escapeSpecialChars(text.substring(start)));
    return sb.toString();
  }

  /**
   * Like GFM autolinking: strip trailing punctuation from a URL, and a trailing ')' only when it is unbalanced so a
   * URL such as https://en.wikipedia.org/wiki/A_(b) is kept whole.
   */
  private static int urlEnd(String text, int start, int end) {
    while (end > start) {
      char c = text.charAt(end - 1);
      if (URL_TRAILING_PUNCTUATION.indexOf(c) != -1) {
        end--;
      } else if (c == ')') {
        String url = text.substring(start, end);
        long open = url.chars().filter(ch -> ch == '(').count();
        long close = url.chars().filter(ch -> ch == ')').count();
        if (close <= open) {
          break;
        }
        end--;
      } else {
        break;
      }
    }
    return end;
  }

  private static String escapeSpecialChars(String s) {
    return SPECIAL_CHARS_PATTERN.matcher(s).replaceAll("\\\\$0");
  }
}
