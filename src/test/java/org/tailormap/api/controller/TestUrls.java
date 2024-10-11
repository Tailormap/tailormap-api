/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller;

public final class TestUrls {
  public static final String layerProvinciesWfs =
      "/app/default/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied";
  public static final String layerBegroeidTerreindeelPostgis =
      "/app/default/layer/lyr:snapshot-geoserver:postgis:begroeidterreindeel";
  public static final String layerWaterdeelOracle =
      "/app/default/layer/lyr:snapshot-geoserver:oracle:WATERDEEL";
  public static final String layerWegdeelSqlServer =
      "/app/default/layer/lyr:snapshot-geoserver:sqlserver:wegdeel";
  public static final String layerOsmPolygonPostgis =
      "/app/default/layer/lyr:snapshot-geoserver:postgis:osm_polygon";
  public static final String layerProxiedWithAuthInPublicApp =
      "/app/default/layer/lyr:bestuurlijkegebieden-proxied:Provinciegebied";
  public static final String layerWaterdeel =
      "/app/default/layer/lyr:snapshot-geoserver:oracle:WATERDEEL";
}
