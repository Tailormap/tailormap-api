/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NOT_MODIFIED;
import static org.tailormap.api.util.Constants.UUID_REGEX;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.persistence.Upload;
import org.tailormap.api.persistence.UploadCategory;
import org.tailormap.api.repository.UploadRepository;
import org.tailormap.api.service.UploadsService;

@RestController
public class UploadsController {
  private final UploadRepository uploadRepository;
  private final UploadsService uploadsService;

  public UploadsController(UploadRepository uploadRepository, UploadsService uploadsService) {
    this.uploadRepository = uploadRepository;
    this.uploadsService = uploadsService;
  }

  // Can't use ${tailormap-api.base-path} because linkTo() won't work
  @GetMapping(path = "/api/uploads/{category}/{id}/{filename}")
  public ResponseEntity<byte[]> getUpload(
      HttpServletRequest request,
      @PathVariable UploadCategory category,
      @PathVariable(name = "id") String idString,
      @PathVariable(required = false) String filename) {

    if (!idString.matches(UUID_REGEX)) {
      return ResponseEntity.badRequest().build();
    }

    UUID id = UUID.fromString(idString);
    long ifModifiedSince = request.getDateHeader("If-Modified-Since");

    if (!uploadsService.checkIfModifiedSince(id, ifModifiedSince)) {
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

  /**
   * Gets the latest upload for a specific category, if any. This is most useful for
   * {@code Upload.CATEGORY_DRAWING_STYLE} .
   */
  @GetMapping("/api/uploads/{category}/latest")
  public ResponseEntity<byte[]> getLatestUpload(@PathVariable UploadCategory category) {
    return uploadRepository
        .findFirstWithContentByCategoryOrderByLastModifiedDesc(category)
        .map(upload -> ResponseEntity.ok()
            .header("Content-Type", upload.getMimeType())
            .header(UploadsService.DESCRIPTION_HEADER_NAME, upload.getDescription())
            .lastModified(upload.getLastModified().toInstant())
            .contentLength(upload.getContentLength())
            .cacheControl(CacheControl.noCache().cachePublic())
            .body(upload.getContent()))
        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
  }
}
