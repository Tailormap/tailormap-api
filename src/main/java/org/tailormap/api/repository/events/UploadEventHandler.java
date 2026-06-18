/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.repository.events;

import static org.tailormap.api.persistence.Upload.CATEGORY_THEME_FAVICON;

import org.springframework.data.rest.core.annotation.HandleAfterCreate;
import org.springframework.data.rest.core.annotation.HandleAfterDelete;
import org.springframework.data.rest.core.annotation.HandleAfterSave;
import org.springframework.data.rest.core.annotation.RepositoryEventHandler;
import org.springframework.stereotype.Component;
import org.tailormap.api.configuration.base.IconResolver;
import org.tailormap.api.persistence.Upload;

@Component
@RepositoryEventHandler
public class UploadEventHandler {
  private final IconResolver iconResolver;

  public UploadEventHandler(IconResolver iconResolver) {
    this.iconResolver = iconResolver;
  }

  @HandleAfterCreate
  @HandleAfterSave
  @HandleAfterDelete
  public void clearIconResolverCache(Upload upload) {
    if (CATEGORY_THEME_FAVICON.equals(upload.getCategory())) {
      iconResolver.clearCache();
    }
  }
}
