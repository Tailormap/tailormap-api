/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.persistence.projections;

import java.time.OffsetDateTime;
import org.springframework.data.rest.core.config.Projection;
import org.tailormap.api.persistence.Upload;

@Projection(
    name = "summary",
    types = {Upload.class})
public interface UploadSummary {
  String getId();

  String getCategory();

  String getFilename();

  String getMimeType();

  Integer getImageWidth();

  Integer getImageHeight();

  Boolean getHiDpiImage();

  OffsetDateTime getLastModified();

  int getContentLength();
}
