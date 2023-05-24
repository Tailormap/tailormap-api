/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository.events;

import io.hypersistence.tsid.TSID;
import java.lang.invoke.MethodHandles;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.helper.GeoServiceHelper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.rest.core.annotation.HandleBeforeCreate;
import org.springframework.data.rest.core.annotation.RepositoryEventHandler;
import org.springframework.stereotype.Component;

@RepositoryEventHandler
@Component
public class GeoServiceEventHandler {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final GeoServiceHelper geoServiceHelper;

  public GeoServiceEventHandler(GeoServiceHelper geoServiceHelper) {
    this.geoServiceHelper = geoServiceHelper;
  }

  @HandleBeforeCreate
  public void assignId(GeoService geoService) throws Exception {
    if (StringUtils.isBlank(geoService.getId())) {
      // We kind of misuse TSIDs here, because we store it as a string. This is because the id
      // string can also be manually assigned. There won't be huge numbers of GeoServices, so it's
      // more of a convenient way to generate an ID that isn't a huge UUID string.
      geoService.setId(TSID.fast().toString());
    }
  }

  @HandleBeforeCreate
  public void handleBeforeCreateOrSave(GeoService geoService) throws Exception {
    logger.info(
        "Loading capabilities before creating geo service from URL: \"{}\"", geoService.getUrl());
    geoServiceHelper.loadServiceCapabilities(geoService);
  }
}
