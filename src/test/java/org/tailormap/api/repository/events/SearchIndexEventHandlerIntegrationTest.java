/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository.events;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumingThat;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.Issue;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.SearchIndex;
import org.tailormap.api.repository.SearchIndexRepository;

@PostgresIntegrationTest
class SearchIndexEventHandlerIntegrationTest {
  @Autowired SearchIndexEventHandler searchIndexEventHandler;
  @Autowired SearchIndexRepository searchIndexRepository;

  /**
   * Test that a {@code SearchIndex} with a scheduled task that already exists cannot be saved with
   * a new task through Spring Data REST.
   */
  @Test
  @Issue("HTM-1258")
  @SuppressWarnings("PMD.UnitTestShouldIncludeAssert")
  void testBeforeSaveSearchIndexEventHandler() {
    SearchIndex existingSearchIndexWithSchedule = searchIndexRepository.findById(1L).orElse(null);

    assumingThat(
        null != existingSearchIndexWithSchedule
            && null != existingSearchIndexWithSchedule.getSchedule()
            && null != existingSearchIndexWithSchedule.getSchedule().getUuid(),
        () -> {
          final Exception actual =
              assertThrows(
                  SchedulerException.class,
                  () -> {
                    existingSearchIndexWithSchedule.getSchedule().setUuid(null);
                    searchIndexEventHandler.beforeSaveSearchIndexEventHandler(
                        existingSearchIndexWithSchedule);
                  },
                  "Test did not invoke expected exception");

          assertThat(actual.getMessage(), containsString("scheduled task already exists"));
        });
  }
}
