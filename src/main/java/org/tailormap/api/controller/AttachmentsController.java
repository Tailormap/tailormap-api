/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import jakarta.validation.Valid;
import java.io.IOException;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.util.factory.GeoTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.annotation.AppRestController;
import org.tailormap.api.geotools.featuresources.AttachmentsHelper;
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.json.AppTreeLayerNode;
import org.tailormap.api.persistence.json.AttachmentAttributeType;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.util.EditUtil;
import org.tailormap.api.viewer.model.AttachmentMetadata;

@AppRestController
@Validated
public class AttachmentsController {

  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final EditUtil editUtil;
  private final FeatureSourceFactoryHelper featureSourceFactoryHelper;
  private final FilterFactory ff = CommonFactoryFinder.getFilterFactory(GeoTools.getDefaultHints());

  public AttachmentsController(EditUtil editUtil, FeatureSourceFactoryHelper featureSourceFactoryHelper) {
    this.editUtil = editUtil;
    this.featureSourceFactoryHelper = featureSourceFactoryHelper;
  }

  /**
   * Add an attachment to a feature
   *
   * @param appTreeLayerNode the application tree layer node
   * @param service the geo service
   * @param layer the geo service layer
   * @param application the application
   * @param featureId the feature id
   * @param attachment the attachment metadata
   * @param fileData the attachment file data
   * @return the response entity
   */
  @PutMapping(
      path = {
        "${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/feature/{featureId}/attachment"
      },
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Transactional
  public ResponseEntity<Serializable> addAttachment(
      @ModelAttribute AppTreeLayerNode appTreeLayerNode,
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer,
      @ModelAttribute Application application,
      @PathVariable String featureId,
      @RequestPart("attachmentMetadata") AttachmentMetadata attachment,
      @RequestPart("attachment") byte[] fileData) {

    editUtil.checkEditAuthorisation();

    TMFeatureType tmFeatureType = editUtil.getEditableFeatureType(application, appTreeLayerNode, service, layer);

    Set<@Valid AttachmentAttributeType> attachmentAttrSet =
        tmFeatureType.getSettings().getAttachmentAttributes();
    if (attachmentAttrSet == null || attachmentAttrSet.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feature type does not support attachments");
    }

    AttachmentAttributeType attachmentAttributeType = attachmentAttrSet.stream()
        .filter(attr -> (attr.getAttributeName().equals(attachment.getAttributeName())
            && java.util.Arrays.stream(attr.getMimeType().split(","))
                .map(String::trim)
                .anyMatch(mime -> mime.equals(attachment.getMimeType()))
            && (attr.getMaxAttachmentSize() == null || attr.getMaxAttachmentSize() >= fileData.length)))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Feature type does not support attachments for attribute "
                + attachment.getAttributeName()
                + " with mime type "
                + attachment.getMimeType()
                + " and size "
                + fileData.length));
    logger.debug("Using attachment attribute {}", attachmentAttributeType);

    if (!checkFeatureExists(tmFeatureType, featureId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feature with id " + featureId + " does not exist");
    }

    AttachmentMetadata response;
    try {
      response = AttachmentsHelper.insertAttachment(tmFeatureType, attachment, featureId, fileData);
    } catch (IOException | SQLException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    return new ResponseEntity<>(response, HttpStatus.CREATED);
  }

  @DeleteMapping(path = "${tailormap-api.base-path}/attachment/{attachmentId}")
  public ResponseEntity<Serializable> deleteAttachment(@PathVariable UUID attachmentId) {
    editUtil.checkEditAuthorisation();

    logger.debug("TODO: Deleting attachment with id {}", attachmentId);
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @GetMapping(
      path = "${tailormap-api.base-path}/attachment/{attachmentId}",
      produces = {"application/octet-stream"})
  // TODO determine return type: ResponseEntity<byte[]> or ResponseEntity<InputStreamResource>?
  public ResponseEntity<byte[]> getAttachment(@PathVariable UUID attachmentId) {
    logger.debug("TODO: Getting attachment with id {}", attachmentId);

    throw new UnsupportedOperationException("Not implemented yet");
  }

  private boolean checkFeatureExists(TMFeatureType tmFeatureType, String featureId) {
    final Filter fidFilter = ff.id(ff.featureId(featureId));
    SimpleFeatureSource fs = null;
    try {
      fs = featureSourceFactoryHelper.openGeoToolsFeatureSource(tmFeatureType);
      return !fs.getFeatures(fidFilter).isEmpty();
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    } finally {
      if (fs != null) {
        fs.getDataStore().dispose();
      }
    }
  }
}
