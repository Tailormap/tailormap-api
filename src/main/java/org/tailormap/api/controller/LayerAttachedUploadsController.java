/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NOT_MODIFIED;
import static org.tailormap.api.util.TMStringUtils.nullIfEmpty;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.commons.lang3.ObjectUtils;
import org.gaul.modernizer_maven_annotations.SuppressModernizer;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.annotation.AppRestController;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.Upload;
import org.tailormap.api.persistence.UploadCategory;
import org.tailormap.api.persistence.json.AppLayerSettings;
import org.tailormap.api.persistence.json.AppTreeLayerNode;
import org.tailormap.api.persistence.json.GeoServiceDefaultLayerSettings;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.persistence.json.GeoServiceLayerSettings;
import org.tailormap.api.repository.UploadRepository;
import org.tailormap.api.service.UploadsService;

@AppRestController
@Validated
public class LayerAttachedUploadsController {
  private final UploadsService uploadsService;
  private final UploadRepository uploadRepository;
  private final UploadsController uploadsController;

  public LayerAttachedUploadsController(
      UploadsService uploadsService, UploadRepository uploadRepository, UploadsController uploadsController) {
    this.uploadsService = uploadsService;
    this.uploadRepository = uploadRepository;
    this.uploadsController = uploadsController;
  }

  @GetMapping(
      path = {
        "${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/uploads/{category}/{id}",
        "${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/uploads/{category}/{id}/{filename}"
      })
  public ResponseEntity<byte[]> getLayerAttachedUpload(
      @ModelAttribute AppTreeLayerNode appTreeLayerNode,
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer,
      @ModelAttribute Application application,
      HttpServletRequest request,
      @PathVariable UploadCategory category,
      @PathVariable(name = "id") UUID id,
      @PathVariable(name = "filename", required = false) String filename) {

    if (UploadCategory.getUnrestrictedCategories().contains(category)) {
      // return from the normal '/uploads' endpoint if the category is not restricted while removing the
      // application and layer from the path. This could happen for "unrestricted" categories like APP_LOGO,
      // UNRESTRICTED, etc. that have been attached to a layer.
      return uploadsController.getUpload(request, category, id.toString(), filename);
    }

    // check that the upload is actually attached to the layer by checking the text of any of the descriptions of
    // the layer
    Pattern pattern =
        Pattern.compile(Pattern.quote(UploadsService.UPLOAD_MARKDOWN_SCHEME) + Pattern.quote(id.toString()));

    GeoServiceDefaultLayerSettings defaultLayerSettings = Optional.ofNullable(
            service.getSettings().getDefaultLayerSettings())
        .orElseGet(GeoServiceDefaultLayerSettings::new);
    GeoServiceLayerSettings serviceLayerSettings = Optional.ofNullable(
            service.getSettings().getLayerSettings().get(layer.getName()))
        .orElseGet(GeoServiceLayerSettings::new);
    AppLayerSettings appLayerSettings = application.getAppLayerSettings(appTreeLayerNode);

    @SuppressModernizer
    // not using Objects.requireNonNullElse(arg1, arg2) because we have 3 options to check for null
    String description = ObjectUtils.firstNonNull(
        nullIfEmpty(appLayerSettings.getDescription()),
        nullIfEmpty(serviceLayerSettings.getDescription()),
        nullIfEmpty(defaultLayerSettings.getDescription()));

    if (description == null
        || description.isBlank()
        || !pattern.matcher(description).find()) {
      throw new ResponseStatusException(
          BAD_REQUEST, "Upload with id '" + id + "' is not attached to layer '" + layer.getName() + "'");
    }

    if (!uploadsService.checkIfModifiedSince(id, request.getDateHeader("If-Modified-Since"))) {
      return ResponseEntity.status(NOT_MODIFIED).build();
    }
    Upload upload = uploadRepository
        .findWithContentByIdAndCategory(id, category)
        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));

    return ResponseEntity.ok()
        .header("Content-Type", upload.getMimeType())
        .header(UploadsService.DESCRIPTION_HEADER_NAME, upload.getDescription())
        .lastModified(upload.getLastModified().toInstant())
        .contentLength(upload.getContentLength())
        .cacheControl(CacheControl.noCache().cachePublic())
        .body(upload.getContent());
  }
}
