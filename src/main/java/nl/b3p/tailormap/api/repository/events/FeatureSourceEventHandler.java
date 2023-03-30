/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository.events;

import nl.b3p.tailormap.api.geotools.featuresources.JDBCFeatureSourceHelper;
import nl.b3p.tailormap.api.persistence.TMFeatureSource;
import org.springframework.data.rest.core.annotation.HandleBeforeCreate;
import org.springframework.data.rest.core.annotation.HandleBeforeSave;
import org.springframework.data.rest.core.annotation.RepositoryEventHandler;
import org.springframework.stereotype.Component;

@RepositoryEventHandler
@Component
public class FeatureSourceEventHandler {

  public FeatureSourceEventHandler() {}

  @HandleBeforeCreate
  @HandleBeforeSave
  public void handleBeforeCreateOrSave(TMFeatureSource featureSource) throws Exception {
    if (featureSource.getProtocol().equals(TMFeatureSource.Protocol.WFS)) {
      return;
    }
    new JDBCFeatureSourceHelper().loadCapabilities(featureSource);
  }
}
