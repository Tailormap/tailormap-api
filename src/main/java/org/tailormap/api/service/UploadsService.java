/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.service;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;

import java.time.temporal.ChronoField;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.tailormap.api.controller.LayerAttachedUploadsController;
import org.tailormap.api.controller.UploadsController;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.Upload;
import org.tailormap.api.persistence.UploadCategory;
import org.tailormap.api.repository.UploadRepository;

@Service
public class UploadsService {
  private final UploadRepository uploadRepository;
  public static final String DESCRIPTION_HEADER_NAME = "TM-Description";
  /** The scheme used in Markdown files to reference an upload. For example, {@code upload://<upload-id>}. */
  public static final String UPLOAD_MARKDOWN_SCHEME = "upload://";

  private static final Pattern UPLOAD_REPLACE_PATTERN =
      Pattern.compile("upload://([a-f0-9]{8}(?:-[a-f0-9]{4}){4}[a-f0-9]{8})");

  public UploadsService(UploadRepository uploadRepository) {
    this.uploadRepository = uploadRepository;
  }

  /**
   * Checks if the upload with the given ID has been modified since the provided timestamp.
   *
   * @param id the UUID of the upload
   * @param ifModifiedSince the timestamp to compare against (in milliseconds)
   * @return true if the upload has been modified since the provided timestamp or when the upload does not exist,
   *     false otherwise
   */
  public boolean checkIfModifiedSince(UUID id, long ifModifiedSince) {
    if (ifModifiedSince == -1) {
      return true;
    }

    return uploadRepository
        .findLastModifiedById(id)
        .map(uploadLastModified -> ifModifiedSince
            < uploadLastModified
                .with(ChronoField.MILLI_OF_SECOND, 0)
                .toInstant()
                .toEpochMilli())
        .orElse(true);
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
    if (category.isRestricted()) {
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
    if (category.isRestricted()) {
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

  public String replaceUploadLinks(Application application, String appLayerId, String description) {
    if (description == null) {
      return null;
    }

    return UPLOAD_REPLACE_PATTERN.matcher(description).replaceAll(matchResult -> {
      try {
        UUID uploadId = UUID.fromString(matchResult.group(1));
        Upload upload = uploadRepository.findById(uploadId).orElse(null);
        if (upload == null) {
          return "";
        }
        return linkTo(LayerAttachedUploadsController.class)
            .slash("api")
            .slash("app")
            .slash(application.getName())
            .slash("layer")
            .slash(appLayerId)
            .slash("uploads")
            .slash(upload.getCategory().toString())
            .slash(uploadId.toString())
            .slash(upload.getFilename())
            .toString();
      } catch (Exception _ignored) {
        return "";
      }
    });
  }

  public static ResponseEntity<byte[]> createUploadResponseEntity(Upload upload) {
    return ResponseEntity.ok()
        .header("Content-Type", upload.getMimeType())
        .header(UploadsService.DESCRIPTION_HEADER_NAME, upload.getDescription())
        .header(
            CONTENT_DISPOSITION,
            ContentDisposition.inline()
                .filename(upload.getFilename())
                .build()
                .toString())
        .lastModified(upload.getLastModified().toInstant())
        .contentLength(upload.getContentLength())
        .cacheControl(CacheControl.noCache().cachePublic())
        .body(upload.getContent());
  }
}
