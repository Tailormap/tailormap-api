/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.persistence.projections;

import java.time.OffsetDateTime;
import nl.b3p.tailormap.api.persistence.Upload;
import org.springframework.data.rest.core.config.Projection;

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
