/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MarkdownUtils {
  /** Characters that have a special meaning in Markdown and are escaped with a backslash. */
  private static final String SPECIAL_CHARACTERS = "\\`*_[]<>#|~+=-";

  /** "http://", "https://" or "www.", grouped because it is concatenated with the other parts. */
  @SuppressWarnings("RegExpUnnecessaryNonCapturingGroup")
  private static final String URL_START = "(?:https?://|www\\.)";

  /** Zero or more characters that are not whitespace, "<" or ">". */
  private static final String URL_BODY = "[^\\s<>]*";

  /** A balanced pair of parentheses at the end, like "(b)" in "wiki/A_(b)". */
  private static final String URL_END_PARENTHESES = "\\([^\\s<>()]*\\)";

  /** Otherwise the last character, which can't be a parenthesis or punctuation from the surrounding text. */
  private static final String URL_END_CHARACTER = "[^\\s<>()*_.,;:!?'\"~]";

  /**
   * A bare URL. It may end with a balanced pair of parentheses (like https://en.wikipedia.org/wiki/A_(b)), but not
   * with punctuation that is more likely to belong to the surrounding text.
   */
  private static final Pattern URL_PATTERN =
      Pattern.compile(URL_START + URL_BODY + "(?:" + URL_END_PARENTHESES + "|" + URL_END_CHARACTER + ")");

  /** An ordered list marker at the start of a line, such as "1." or "12)". */
  private static final Pattern ORDERED_LIST_MARKER = Pattern.compile("^([ \\t]*\\d+)([.)])", Pattern.MULTILINE);

  private MarkdownUtils() {}

  /**
   * Escapes plain text so it is rendered literally as Markdown. Bare URLs are left untouched so they are still
   * autolinked (and rendered as images by the viewer). Newlines are not changed.
   *
   * @param text the plain text, may be null
   * @return the escaped text, or null if the text is null
   */
  public static String escapeMarkdown(String text) {
    if (text == null) {
      return null;
    }
    StringBuilder result = new StringBuilder();
    Matcher url = URL_PATTERN.matcher(text);
    int start = 0;
    while (url.find()) {
      escapeSpecialCharacters(text.substring(start, url.start()), result);
      result.append(url.group());
      start = url.end();
    }
    escapeSpecialCharacters(text.substring(start), result);

    // URLs never start with a digit, so this does not change them
    return ORDERED_LIST_MARKER.matcher(result).replaceAll("$1\\\\$2");
  }

  private static void escapeSpecialCharacters(String text, StringBuilder result) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (SPECIAL_CHARACTERS.indexOf(c) != -1) {
        result.append('\\');
      }
      result.append(c);
    }
  }
}
