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
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.geotools.api.data.Query;
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

    checkFeatureExists(tmFeatureType, featureId);

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

    AttachmentMetadata response;
    try {
      response = AttachmentsHelper.insertAttachment(tmFeatureType, attachment, featureId, fileData);
    } catch (IOException | SQLException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    return new ResponseEntity<>(response, HttpStatus.CREATED);
  }

  /**
   * Add an attachment to a feature
   *
   * @param appTreeLayerNode the application tree layer node
   * @param service the geo service
   * @param layer the geo service layer
   * @param application the application
   * @param featureId the feature id
   * @return the response entity
   */
  @GetMapping(
      path = {
        "${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/feature/{featureId}/attachment"
      },
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Transactional
  public ResponseEntity<List<AttachmentMetadata>> listAttachments(
      @ModelAttribute AppTreeLayerNode appTreeLayerNode,
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer,
      @ModelAttribute Application application,
      @PathVariable String featureId) {

    TMFeatureType tmFeatureType = editUtil.getEditableFeatureType(application, appTreeLayerNode, service, layer);

    checkFeatureExists(tmFeatureType, featureId);
    checkFeatureTypeSupportsAttachments(tmFeatureType);

    List<AttachmentMetadata> response;
    try {
      response = AttachmentsHelper.listAttachmentsForFeature(tmFeatureType, featureId);
    } catch (IOException | SQLException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @DeleteMapping(
      path = "${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/attachment/{attachmentId}")
  public ResponseEntity<Serializable> deleteAttachment(
      @ModelAttribute AppTreeLayerNode appTreeLayerNode,
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer,
      @ModelAttribute Application application,
      @PathVariable UUID attachmentId) {
    editUtil.checkEditAuthorisation();

    TMFeatureType tmFeatureType = editUtil.getEditableFeatureType(application, appTreeLayerNode, service, layer);

    checkFeatureTypeSupportsAttachments(tmFeatureType);

    try {
      AttachmentsHelper.deleteAttachment(attachmentId, tmFeatureType);
    } catch (IOException | SQLException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
  }

  @Transactional
  @GetMapping(
      path = "${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/attachment/{attachmentId}",
      produces = {"application/octet-stream"})
  public ResponseEntity<byte[]> getAttachment(
      @ModelAttribute AppTreeLayerNode appTreeLayerNode,
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer,
      @ModelAttribute Application application,
      @PathVariable UUID attachmentId) {

    TMFeatureType tmFeatureType = editUtil.getEditableFeatureType(application, appTreeLayerNode, service, layer);

    try {
      final AttachmentsHelper.AttachmentWithBinary attachmentWithBinary =
          AttachmentsHelper.getAttachment(tmFeatureType, attachmentId);

      if (attachmentWithBinary == null) {
        throw new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Attachment %s not found".formatted(attachmentId.toString()));
      }

      // the binary attachment() is a read-only ByteBuffer, so we cant use .array()
      final ByteBuffer bb = attachmentWithBinary.attachment().asReadOnlyBuffer();
      bb.rewind();
      byte[] attachmentData = new byte[bb.remaining()];
      bb.get(attachmentData);

      return ResponseEntity.ok()
          .header(
              "Content-Disposition",
              "inline; filename=\""
                  + attachmentWithBinary.attachmentMetadata().getFileName() + "\"")
          .contentType(MediaType.parseMediaType(
              attachmentWithBinary.attachmentMetadata().getMimeType()))
          .body(attachmentData);
    } catch (SQLException | IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  private void checkFeatureExists(TMFeatureType tmFeatureType, String featureId) throws ResponseStatusException {
    final Filter fidFilter = ff.id(ff.featureId(featureId));
    SimpleFeatureSource fs = null;
    try {
      fs = featureSourceFactoryHelper.openGeoToolsFeatureSource(tmFeatureType);
      Query query = new Query();
      query.setFilter(fidFilter);
      if (fs.getCount(query) < 1) {
        throw new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Feature with id " + featureId + " does not exist");
      }
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    } finally {
      if (fs != null) {
        fs.getDataStore().dispose();
      }
    }
  }

  private void checkFeatureTypeSupportsAttachments(TMFeatureType tmFeatureType) throws ResponseStatusException {
    Set<@Valid AttachmentAttributeType> attachmentAttrSet =
        tmFeatureType.getSettings().getAttachmentAttributes();
    if (attachmentAttrSet == null || attachmentAttrSet.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feature type does not support attachments");
    }
  }
}
