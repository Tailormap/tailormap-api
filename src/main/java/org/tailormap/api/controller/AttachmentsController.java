/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import com.google.common.base.Splitter;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.data.simple.SimpleFeatureIterator;
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
import org.springframework.web.bind.annotation.PostMapping;
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
  @PostMapping(
      path = {
        "${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/feature/{featureId}/attachments"
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

    Object primaryKey = getFeaturePrimaryKeyByFid(tmFeatureType, featureId);

    Set<@Valid AttachmentAttributeType> attachmentAttrSet =
        tmFeatureType.getSettings().getAttachmentAttributes();
    if (attachmentAttrSet == null || attachmentAttrSet.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Layer does not support attachments");
    }

    AttachmentAttributeType attachmentAttribute = attachmentAttrSet.stream()
        .filter(attr -> attr.getAttributeName().equals(attachment.getAttributeName()))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Layer does not support attachments for attribute " + attachment.getAttributeName()));

    if (attachmentAttribute.getMaxAttachmentSize() != null
        && attachmentAttribute.getMaxAttachmentSize() < fileData.length) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Attachment size %d exceeds maximum of %d"
              .formatted(fileData.length, attachmentAttribute.getMaxAttachmentSize()));
    }

    if (attachmentAttribute.getMimeType() != null) {
      if (!validateMimeTypeAccept(
          attachmentAttribute.getMimeType(), attachment.getFileName(), attachment.getMimeType())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File type or extension not allowed");
      }
    }

    logger.debug("Using attachment attribute {}", attachmentAttribute);

    AttachmentMetadata response;
    try {
      response = AttachmentsHelper.insertAttachment(tmFeatureType, attachment, primaryKey, fileData);
    } catch (IOException | SQLException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    return new ResponseEntity<>(response, HttpStatus.CREATED);
  }

  /**
   * Validate as <a href="https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/input/file#accept">file
   * input "accept" attribute</a>.
   *
   * @param acceptList comma-separated list of MIME types and file extensions to validate against
   * @param fileName name of the file to validate
   * @param mimeType MIME type of the file to validate
   * @return true if the file's extension or MIME type matches one of the accepted types, false otherwise
   */
  private static boolean validateMimeTypeAccept(String acceptList, String fileName, String mimeType) {
    Iterable<String> allowedMimeTypes =
        Splitter.on(Pattern.compile(",\\s*")).split(acceptList);
    final Locale locale = Locale.ENGLISH;
    for (String allowedType : allowedMimeTypes) {
      if (allowedType.startsWith(".")) {
        // Check file extension
        if (fileName.toLowerCase(locale).endsWith(allowedType.toLowerCase(locale))) {
          return true;
        }
      } else if (allowedType.endsWith("/*")) {
        // Check mime type category (e.g. image/*)
        String category = allowedType.substring(0, allowedType.length() - 1);
        if (mimeType.startsWith(category)) {
          return true;
        }
      } else {
        // Check exact mime type match
        if (mimeType.equals(allowedType)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * List attachments for a feature.
   *
   * @param appTreeLayerNode the application tree layer node
   * @param service the geo service
   * @param layer the geo service layer
   * @param application the application
   * @param featureId the feature id
   * @return the response entity containing a list of attachment metadata
   */
  @GetMapping(
      path = {
        "${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/feature/{featureId}/attachments"
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

    checkFeatureTypeSupportsAttachments(tmFeatureType);
    Object primaryKey = getFeaturePrimaryKeyByFid(tmFeatureType, featureId);

    List<AttachmentMetadata> response;
    try {
      response = AttachmentsHelper.listAttachmentsForFeature(tmFeatureType, primaryKey);
    } catch (IOException | SQLException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    return new ResponseEntity<>(response, HttpStatus.OK);
  }

  @DeleteMapping(
      path = "${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/attachment/{attachmentId}")
  @Transactional
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

  private Object getFeaturePrimaryKeyByFid(TMFeatureType tmFeatureType, String featureId)
      throws ResponseStatusException {
    final Filter fidFilter = ff.id(ff.featureId(featureId));
    SimpleFeatureSource fs = null;
    try {
      fs = featureSourceFactoryHelper.openGeoToolsFeatureSource(tmFeatureType);
      Query query = new Query();
      query.setFilter(fidFilter);
      query.setPropertyNames(tmFeatureType.getPrimaryKeyAttribute());
      try (SimpleFeatureIterator sfi = fs.getFeatures(query).features()) {
        if (!sfi.hasNext()) {
          throw new ResponseStatusException(
              HttpStatus.NOT_FOUND, "Feature with id %s does not exist".formatted(featureId));
        }
        return sfi.next().getAttribute(tmFeatureType.getPrimaryKeyAttribute());
      }
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
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
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Layer does not support attachments");
    }
  }
}
