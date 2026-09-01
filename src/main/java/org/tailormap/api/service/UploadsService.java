/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.service;

import java.time.temporal.ChronoField;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.tailormap.api.repository.UploadRepository;

@Service
public class UploadsService {
  private final UploadRepository uploadRepository;
  public static final String DESCRIPTION_HEADER_NAME = "TM-Description";
  /** The scheme used in Markdown files to reference an upload. For example, {@code upload://<upload-id>}. */
  public static final String UPLOAD_MARKDOWN_SCHEME = "upload://";

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
}
