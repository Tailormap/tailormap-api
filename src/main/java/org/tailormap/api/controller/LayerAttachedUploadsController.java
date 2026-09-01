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
        /* Can't use ${tailormap-api.base-path} because linkTo() used in UploadHelper#getUrlForLayerAttachedImage() may not work */
        "/api/app/{viewerName}/layer/{appLayerId}/uploads/{category}/{id}",
        "/api/app/{viewerName}/layer/{appLayerId}/uploads/{category}/{id}/{filename}"
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
      return uploadsController.getUpload(request, category, id, filename);
    }

    switch (category) {
      case LAYER_ATTACHED_FILE ->
        validateUploadIsInDescription(id, service, layer, application, appTreeLayerNode);
      case LEGEND -> {
        validateLegendIsAttached(id, service, layer, application, appTreeLayerNode);
      }
      default ->
        throw new ResponseStatusException(
            BAD_REQUEST, "Uploads for category " + category + " are not accessible via this endpoint");
    }

    if (!uploadsService.checkIfModifiedSince(id, request.getDateHeader("If-Modified-Since"))) {
      return ResponseEntity.status(NOT_MODIFIED).build();
    }
    // TODO this would fail when we have added a LEGEND upload to a layer description, because the category would be
    //  wrong, since the frontend generating the url does not know the category of the upload, so it is likely to
    //  always use LAYER_ATTACHED_FILE.
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

  /**
   * check that the upload is actually attached to the layer by checking the text of any of the descriptions of the
   * layer
   *
   * @param id the upload id
   * @param service the GeoService the layer belongs to
   * @param layer the layer the upload is attached to
   * @param application the application the layer belongs to
   * @param appTreeLayerNode the application tree node for the layer
   * @throws ResponseStatusException if the upload is not attached to the layer
   */
  private void validateUploadIsInDescription(
      UUID id,
      GeoService service,
      GeoServiceLayer layer,
      Application application,
      AppTreeLayerNode appTreeLayerNode)
      throws ResponseStatusException {

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
  }
  /**
   * check that the legend is actually attached to the layer by checking the legend fields and the text of any of the
   * descriptions of the layer.
   *
   * @param id the upload id
   * @param service the GeoService the layer belongs to
   * @param layer the layer the upload is attached to
   * @param application the application the layer belongs to
   * @param appTreeLayerNode the application tree node for the layer
   * @throws ResponseStatusException if the legend is not attached to the layer
   */
  private void validateLegendIsAttached(
      UUID id,
      GeoService service,
      GeoServiceLayer layer,
      Application application,
      AppTreeLayerNode appTreeLayerNode)
      throws ResponseStatusException {

    GeoServiceDefaultLayerSettings defaultLayerSettings = Optional.ofNullable(
            service.getSettings().getDefaultLayerSettings())
        .orElseGet(GeoServiceDefaultLayerSettings::new);

    GeoServiceLayerSettings serviceLayerSettings = Optional.ofNullable(
            service.getSettings().getLayerSettings().get(layer.getName()))
        .orElseGet(GeoServiceLayerSettings::new);

    @SuppressModernizer
    // not using Objects.requireNonNullElse(arg1, arg2) because we never want to throw an NPE here, we just want to
    // check if the legendImageId is set in either the service layer settings or the default layer settings
    String legendImageId = ObjectUtils.firstNonNull(
        serviceLayerSettings.getLegendImageId(), defaultLayerSettings.getLegendImageId());

    UUID legendUuid = null;
    if (legendImageId != null && !legendImageId.isBlank()) {
      try {
        legendUuid = UUID.fromString(legendImageId);
      } catch (IllegalArgumentException ignored) {
        // Invalid UUID configured; treat as "not attached" and fall back to the description check below.
      }
    }

    if (!id.equals(legendUuid)) {
      // could be a bad request, but a legend could also be in the description, so check that as well
      validateUploadIsInDescription(id, service, layer, application, appTreeLayerNode);
    }
  }
}
