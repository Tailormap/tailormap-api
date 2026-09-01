/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller.admin;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.io.IOException;
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
import org.tailormap.api.persistence.Upload;
import org.tailormap.api.persistence.UploadCategory;
import org.tailormap.api.repository.UploadMatch;
import org.tailormap.api.repository.UploadRepository;
import org.tailormap.api.service.UploadsService;
import org.tailormap.api.service.ZipService;
import org.tailormap.api.viewer.model.ErrorResponse;

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
  @GetMapping(path = "${tailormap-api.admin.base-path}/uploads/{uuids}", produces = "application/zip")
  public byte[] downloadUploadsByCategory(@PathVariable("uuids") List<UUID> uuids) throws IOException {
    // Authorisation check isn't needed: only admins are allowed on the admin base path
    Path tempDir = Files.createTempDirectory("admin-uploads-");
    try {
      for (Upload upload : uploadRepository.findAllWithContentByIdIn(uuids)) {
        // validate/sanitise filename: no directories allowed, only the filename itself
        String safeFilename =
            Path.of(upload.getFilename()).getFileName().toString();
        Path filePath = tempDir.resolve(safeFilename);
        Files.write(filePath, upload.getContent());
      }

      Path zipFile = Files.createTempFile("admin-uploads-", ".zip");
      logger.info("Created zip file {}", zipFile.toAbsolutePath());

      try {
        zipService.zipDirectory(tempDir, zipFile);
        return Files.readAllBytes(zipFile);
      } finally {
        Files.deleteIfExists(zipFile);
      }
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
        "${tailormap-api.admin.base-path}/uploads/{category}/{id}",
        "${tailormap-api.admin.base-path}/uploads/{category}/{id}/{filename}"
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

  @DeleteMapping(path = "${tailormap-api.admin.base-path}/uploads/{uuids}")
  public void deleteUploadsByCategory(@PathVariable("uuids") List<UUID> uuids) throws IllegalArgumentException {
    uploadRepository.deleteAllById(uuids);
  }
}
