/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.persistence.helper;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.tailormap.api.controller.LayerAttachedUploadsController;
import org.tailormap.api.controller.UploadsController;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.UploadCategory;
import org.tailormap.api.repository.UploadRepository;

@Service
public class UploadHelper {

  private final UploadRepository uploadRepository;

  public UploadHelper(UploadRepository uploadRepository) {
    this.uploadRepository = uploadRepository;
  }

  public String getUrlForImage(String imageId, UploadCategory category) {
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

  public String getUrlForImage(UUID imageId, UploadCategory category) {
    if (imageId == null) {
      return null;
    }
    if (UploadCategory.getRestrictedCategories().contains(category)) {
      throw new IllegalArgumentException(
          "Access to restricted category is not allowed without application and layer context");
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

  /**
   * Returns the URL for a layer-attached image e.g. a legend image, or null if the imageId is null or not found.
   *
   * @param imageId the id of the image
   * @param category the category of the image, e.g. LEGEND
   * @param application the application the image is attached to
   * @param layerId the id of the layer the image is attached to
   * @return the URL for the layer-attached image or null
   */
  public String getUrlForLayerAttachedImage(
      UUID imageId, UploadCategory category, Application application, String layerId) {
    if (imageId == null) {
      return null;
    }
    if (UploadCategory.getRestrictedCategories().contains(category)) {
      return uploadRepository
          .findByIdAndCategory(imageId, category)
          .map(upload -> linkTo(LayerAttachedUploadsController.class)
              .slash("api")
              .slash("app")
              .slash(application.getName())
              .slash("layer")
              .slash(layerId)
              .slash("uploads")
              .slash(category)
              .slash(imageId.toString())
              .slash(upload.getFilename())
              .toString())
          .orElse(null);
    } else {
      return getUrlForImage(imageId, category);
    }
  }
}
