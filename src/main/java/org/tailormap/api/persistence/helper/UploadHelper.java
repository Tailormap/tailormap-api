/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.persistence.helper;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

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
    if (imageId == null) {
      return null;
    }
    try {
      UUID uuid = UUID.fromString(imageId);
      return getUrlForImage(uuid, category);
    } catch (IllegalArgumentException e) {
      // Illegal UUID, return null
      return null;
    }
  }

  public String getUrlForImage(UUID imageId, String category) {
    if (imageId == null) {
      return null;
    }
    return uploadRepository
        .findByIdAndCategory(imageId, category)
        .map(upload -> linkTo(UploadsController.class)
            .slash("api")
            .slash("uploads")
            .slash(category)
            .slash(imageId.toString())
            .slash(upload.getFilename())
            .toString())
        .orElse(null);
  }
}
