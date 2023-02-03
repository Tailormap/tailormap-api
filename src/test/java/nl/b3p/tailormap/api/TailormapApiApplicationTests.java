/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api;

// import nl.b3p.tailormap.api.repository.ConfigurationRepository;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * testcases for {@link TailormapApiApplication}.
 *
 * @since 0.1
 */
@SpringBootTest(/*,classes = {JPAConfiguration.class  ConfigurationRepository.class}}*/ )
@ActiveProfiles("test")
class TailormapApiApplicationTests {

  @Test
  @SuppressWarnings({"PMD.JUnitTestsShouldIncludeAssert", "EmptyMethod"})
  void contextLoads() {
    /* empty by design */
  }
}
