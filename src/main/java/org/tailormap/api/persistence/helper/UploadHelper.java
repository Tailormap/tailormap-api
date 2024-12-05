/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.persistence.helper;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.tailormap.api.controller.UploadsController;
import org.tailormap.api.repository.UploadRepository;

@Service
public class UploadHelper {

  private final UploadRepository uploadRepository;

  public UploadHelper(UploadRepository uploadRepository) {
    this.uploadRepository = uploadRepository;
  }

  public String getUrlForImage(String imageId, String category) {
    return Optional.ofNullable(imageId)
        .map(UUID::fromString)
        .flatMap(imageUuid -> uploadRepository.findByIdAndCategory(imageUuid, category))
        .map(
            upload ->
                linkTo(
                        UploadsController.class,
                        Map.of(
                            "id", imageId, "category", category, "filename", upload.getFilename()))
                    .toString())
        .orElse(null);
  }
}
