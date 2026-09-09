/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller.admin;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.tailormap.api.persistence.Upload;
import org.tailormap.api.persistence.UploadCategory;
import org.tailormap.api.repository.UploadMatch;
import org.tailormap.api.repository.UploadRepository;
import org.tailormap.api.service.UploadsService;
import org.tailormap.api.service.ZipService;
import org.tailormap.api.viewer.model.ErrorResponse;

// Note on paths: make sure they do not clash with Spring Data REST paths:  /uploads/{variable} also matches
// /uploads/search/ which is used by Spring Data REST.

@RestController
public class UploadsAdminController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final UploadRepository uploadRepository;
  private final ZipService zipService;

  public UploadsAdminController(UploadRepository uploadRepository, ZipService zipService) {
    this.uploadRepository = uploadRepository;
    this.zipService = zipService;
  }

  @ExceptionHandler({IOException.class})
  public ResponseEntity<?> handleException(Exception ex) {
    // wrap the exception in a proper json response
    return ResponseEntity.internalServerError()
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ErrorResponse()
            .message(
                ex.getMessage() != null
                    ? ex.getMessage()
                    : HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
            .code(HttpStatus.INTERNAL_SERVER_ERROR.value()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<?> handleIllegalArgumentException(IllegalArgumentException ex) {
    // wrap the exception in a proper json response
    return ResponseEntity.badRequest()
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ErrorResponse()
            .message(ex.getMessage() != null ? ex.getMessage() : HttpStatus.BAD_REQUEST.getReasonPhrase())
            .code(HttpStatus.BAD_REQUEST.value()));
  }

  @PostMapping(
      path = "${tailormap-api.admin.base-path}/uploads/find-by-hash/{category}",
      consumes = "application/json")
  public List<UploadMatch> findUploadsByHash(
      @PathVariable UploadCategory category, @RequestBody List<String> hashes) {
    return uploadRepository.findByHashIn(category, hashes);
  }

  @Transactional(readOnly = true)
  @PostMapping(path = "${tailormap-api.admin.base-path}/uploads/multi", produces = "application/zip")
  public ResponseEntity<StreamingResponseBody> downloadUploads(@RequestBody List<UUID> uuids) throws IOException {
    // Authorization check isn't needed: only admins are allowed on the admin base path
    Path tempDir = Files.createTempDirectory("admin-uploads-");
    try {
      List<Upload> uploads = uploadRepository.findAllWithContentByIdIn(uuids);
      if (uploads.isEmpty()) {
        // Do not return an empty zip file
        throw new ResponseStatusException(NOT_FOUND);
      }
      for (Upload upload : uploads) {
        // validate/sanitize filename: no directories allowed, only the filename itself
        String safeFilename =
            Path.of(upload.getFilename()).getFileName().toString();
        Path filePath = tempDir.resolve(safeFilename);
        Files.write(filePath, upload.getContent());
      }

      Path zipFile = Files.createTempFile("admin-uploads-", ".zip");
      logger.info("Created zip file {}", zipFile.toAbsolutePath());

      zipService.zipDirectory(tempDir, zipFile);

      StreamingResponseBody response = outputStream -> {
        try (InputStream inputStream = Files.newInputStream(zipFile)) {
          inputStream.transferTo(outputStream);
        } finally {
          Files.deleteIfExists(zipFile);
          logger.debug("Deleted zip file {}", zipFile.toAbsolutePath());
        }
      };

      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .header("Content-Disposition", "attachment; filename=\"uploads.zip\"")
          .contentLength(Files.size(zipFile))
          .body(response);
    } finally {
      try (Stream<Path> pathStream = Files.walk(tempDir)) {
        pathStream.sorted(Comparator.reverseOrder()).forEach(path -> {
          try {
            Files.delete(path);
          } catch (IOException e) {
            // Ignore
          }
        });
      }
    }
  }

  @GetMapping(
      path = {
        "${tailormap-api.admin.base-path}/uploads/download/{category}/{id}",
        "${tailormap-api.admin.base-path}/uploads/download/{category}/{id}/{filename}"
      })
  public ResponseEntity<byte[]> getUpload(
      @PathVariable UploadCategory category,
      @PathVariable(name = "id") UUID id,
      @PathVariable(required = false) String filename) {

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

  @DeleteMapping(path = "${tailormap-api.admin.base-path}/uploads/multi")
  public void deleteUploads(@RequestBody List<UUID> uuids) throws IllegalArgumentException {
    uploadRepository.deleteAllById(uuids);
  }
}
