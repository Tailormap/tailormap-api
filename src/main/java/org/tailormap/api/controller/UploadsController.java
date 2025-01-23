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
import java.time.OffsetDateTime;
import java.time.temporal.ChronoField;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.persistence.Upload;
import org.tailormap.api.repository.UploadRepository;

@RestController
// Can't use ${tailormap-api.base-path} because linkTo() won't work
@RequestMapping(path = "/api/uploads/{category}/{id}/{filename}")
public class UploadsController {
  private final UploadRepository uploadRepository;

  public UploadsController(UploadRepository uploadRepository) {
    this.uploadRepository = uploadRepository;
  }

  @GetMapping
  public ResponseEntity<byte[]> getUpload(
      HttpServletRequest request,
      @PathVariable String category,
      @PathVariable(name = "id") String idString,
      @PathVariable(required = false) String filename) {

    if (!idString.matches(UUID_REGEX)) {
      return ResponseEntity.badRequest().build();
    }

    UUID id = UUID.fromString(idString);
    long ifModifiedSince = request.getDateHeader("If-Modified-Since");
    if (ifModifiedSince != -1) {
      OffsetDateTime uploadLastModified =
          uploadRepository.findLastModifiedById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
      if (ifModifiedSince
          >= uploadLastModified
              .with(ChronoField.MILLI_OF_SECOND, 0)
              .toInstant()
              .toEpochMilli()) {
        return ResponseEntity.status(NOT_MODIFIED).build();
      }
    }

    Upload upload = uploadRepository
        .findWithContentByIdAndCategory(id, category)
        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));

    return ResponseEntity.ok()
        .header("Content-Type", upload.getMimeType())
        .lastModified(upload.getLastModified().toInstant())
        .contentLength(upload.getContentLength())
        .cacheControl(CacheControl.noCache().cachePublic())
        .body(upload.getContent());
  }
}
