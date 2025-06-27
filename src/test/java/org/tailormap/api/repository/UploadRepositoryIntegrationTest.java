/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Upload;

@PostgresIntegrationTest
class UploadRepositoryIntegrationTest {

  @Autowired
  private UploadRepository uploadRepository;

  @Test
  @Transactional(label = "inserts and removes an upload")
  void should_find_latest_upload_by_category() {
    Upload testUpload =
        uploadRepository.findByCategory(Upload.CATEGORY_DRAWING_STYLE).get(0);
    // create a copy of the upload with a different filename and younger last-modified time
    Upload copiedUpload = new Upload()
        .setCategory(testUpload.getCategory())
        .setFilename(testUpload.getFilename())
        .setContent(testUpload.getContent())
        .setMimeType(testUpload.getMimeType())
        .setLastModified(Instant.now().atOffset(ZoneOffset.UTC))
        .setFilename("latest-drawing-style.json");
    uploadRepository.save(copiedUpload);

    Optional<Upload> upload =
        uploadRepository.findFirstWithContentByCategoryOrderByLastModifiedDesc(Upload.CATEGORY_DRAWING_STYLE);
    assertTrue(upload.isPresent(), "A latest upload should be present for the drawing style category");
    assertEquals(
        2,
        uploadRepository.findByCategory(Upload.CATEGORY_DRAWING_STYLE).size(),
        "There should be two uploads for the drawing style category after adding the latest upload");

    final Upload actual = upload.orElseThrow();
    assertEquals(Upload.CATEGORY_DRAWING_STYLE, actual.getCategory());
    assertEquals(
        "application/json",
        actual.getMimeType(),
        "Mime type should be application/json for drawing style uploads");
    assertEquals(
        "latest-drawing-style.json",
        actual.getFilename(),
        "Name should be latest-drawing-style.json for the latest drawing style upload");
  }
}
