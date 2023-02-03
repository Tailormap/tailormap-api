/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TailormapApiApplicationTests {

  @Test
  @SuppressWarnings({"PMD.JUnitTestsShouldIncludeAssert", "EmptyMethod"})
  void contextLoads() {
    /* empty by design */
  }
}
