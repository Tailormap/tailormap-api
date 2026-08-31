/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Upload;
import org.tailormap.api.persistence.UploadCategory;
import org.tailormap.api.repository.UploadRepository;

@PostgresIntegrationTest
class UploadsServiceIntegrationTest {
  private static OffsetDateTime now;

  @Autowired
  private UploadsService uploadsService;

  @Autowired
  private UploadRepository uploadRepository;

  @BeforeAll
  static void setup() {
    now = OffsetDateTime.now(ZoneId.systemDefault());
  }

  @Test
  void check_if_modified_since_two_weeks_ago() {
    Upload logo = uploadRepository.findByCategory(UploadCategory.APP_LOGO).getFirst();

    long twoWeeksBeforeNow = now.minusWeeks(2).toInstant().toEpochMilli();

    assertTrue(uploadsService.checkIfModifiedSince(logo.getId(), twoWeeksBeforeNow));
  }

  @Test
  void check_if_modified_since_a_week_later() {
    Upload logo = uploadRepository.findByCategory(UploadCategory.APP_LOGO).getFirst();

    long aWeekInTheFuture = now.plusWeeks(1).toInstant().toEpochMilli();

    assertFalse(uploadsService.checkIfModifiedSince(logo.getId(), aWeekInTheFuture));
  }

  @Test
  void check_if_modified_since_not_found() {
    assertTrue(
        uploadsService.checkIfModifiedSince(UUID.randomUUID(), System.currentTimeMillis()),
        "Should return true for non-existing upload");
  }
}
