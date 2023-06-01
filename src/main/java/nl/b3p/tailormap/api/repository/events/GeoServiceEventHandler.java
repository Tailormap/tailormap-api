/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository.events;

import io.hypersistence.tsid.TSID;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.helper.GeoServiceHelper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.rest.core.annotation.HandleBeforeCreate;
import org.springframework.data.rest.core.annotation.RepositoryEventHandler;
import org.springframework.stereotype.Component;

@RepositoryEventHandler
@Component
public class GeoServiceEventHandler {
  private final GeoServiceHelper geoServiceHelper;

  public GeoServiceEventHandler(GeoServiceHelper geoServiceHelper) {
    this.geoServiceHelper = geoServiceHelper;
  }

  @HandleBeforeCreate
  public void assignId(GeoService geoService) {
    if (StringUtils.isBlank(geoService.getId())) {
      // We kind of misuse TSIDs here, because we store it as a string. This is because the id
      // string can also be manually assigned. There won't be huge numbers of GeoServices, so it's
      // more of a convenient way to generate an ID that isn't a huge UUID string.
      geoService.setId(TSID.fast().toString());
    }
  }

  @HandleBeforeCreate
  public void loadCapabilities(GeoService geoService) throws Exception {
    geoServiceHelper.loadServiceCapabilities(geoService);
  }
}
