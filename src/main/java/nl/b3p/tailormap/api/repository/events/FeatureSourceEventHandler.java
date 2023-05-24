/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository.events;

import nl.b3p.tailormap.api.geotools.featuresources.JDBCFeatureSourceHelper;
import nl.b3p.tailormap.api.geotools.featuresources.WFSFeatureSourceHelper;
import nl.b3p.tailormap.api.persistence.TMFeatureSource;
import org.springframework.data.rest.core.annotation.HandleBeforeCreate;
import org.springframework.data.rest.core.annotation.RepositoryEventHandler;
import org.springframework.stereotype.Component;

@RepositoryEventHandler
@Component
public class FeatureSourceEventHandler {

  public FeatureSourceEventHandler() {}

  @HandleBeforeCreate
  public void loadCapabilities(TMFeatureSource featureSource) throws Exception {
    if (featureSource.getProtocol().equals(TMFeatureSource.Protocol.WFS)) {
      new WFSFeatureSourceHelper().loadCapabilities(featureSource);
    } else if (featureSource.getProtocol().equals(TMFeatureSource.Protocol.JDBC)) {
      new JDBCFeatureSourceHelper().loadCapabilities(featureSource);
    }
  }
}
