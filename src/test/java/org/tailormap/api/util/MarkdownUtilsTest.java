/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MarkdownUtilsTest {
  @Test
  void null_and_empty() {
    assertNull(MarkdownUtils.escapeMarkdown(null));
    assertEquals("", MarkdownUtils.escapeMarkdown(""));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
plain text, nothing special. | plain text, nothing special.
**bold** and _italic_ | \\*\\*bold\\*\\* and \\_italic\\_
snake_case_name | snake\\_case\\_name
[label](https://example.com) | \\[label\\](https://example.com)
![img](x.png) | !\\[img\\](x.png)
`code` | \\`code\\`
<script>alert(1)</script> | \\<script\\>alert(1)\\</script\\>
"# not a heading" | "\\# not a heading"
"a | b | c" | "a \\| b \\| c"
~~strike~~ | \\~\\~strike\\~\\~
back\\slash | back\\\\slash
""")
  void escapes_special_characters(String input, String expected) {
    assertEquals(expected, MarkdownUtils.escapeMarkdown(input));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
- item | \\- item
+ item | \\+ item
"  - indented item" | "  \\- indented item"
--- | \\-\\-\\-
=== | \\=\\=\\=
1. first | 1\\. first
12) twelfth | 12\\) twelfth
""")
  void escapes_block_markers_at_line_start(String input, String expected) {
    assertEquals(expected, MarkdownUtils.escapeMarkdown(input));
  }

  @Test
  void escapes_block_markers_at_start_of_every_line() {
    assertEquals(
        "Items:\n\\- one\r\n\\- two\n1\\. three\n\n\\=\\=",
        MarkdownUtils.escapeMarkdown("Items:\n- one\r\n- two\n1. three\n\n=="));
  }

  @Test
  void leaves_newlines_untouched() {
    String text = "line 1\nline 2\r\n\r\nline 3";
    assertEquals(text, MarkdownUtils.escapeMarkdown(text));
  }

  @ParameterizedTest
  @CsvSource(delimiter = '|', quoteCharacter = '"', textBlock = """
https://example.com/some_path/a-b_c.png | https://example.com/some_path/a-b_c.png
see https://example.com/a_b. | see https://example.com/a_b.
see http://example.com/a_b, and *this* | see http://example.com/a_b, and \\*this\\*
*https://example.com/a_b* | \\*https://example.com/a_b\\*
_www.example.com/x_y_ | \\_www.example.com/x_y\\_
(https://en.wikipedia.org/wiki/A_(b)) | (https://en.wikipedia.org/wiki/A_(b))
https://x.nl/getimage.ashx?id=1&a_b=2 | https://x.nl/getimage.ashx?id=1&a_b=2
<https://example.com/a_b> | \\<https://example.com/a_b\\>
""")
  void leaves_urls_untouched(String input, String expected) {
    assertEquals(expected, MarkdownUtils.escapeMarkdown(input));
  }

  @Test
  void url_at_line_start() {
    assertEquals(
        "Data:\nhttps://example.com/a_b\n\\- https://example.com/c_d",
        MarkdownUtils.escapeMarkdown("Data:\nhttps://example.com/a_b\n- https://example.com/c_d"));
  }
}
