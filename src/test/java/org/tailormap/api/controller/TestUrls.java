/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller;

public interface TestUrls {
  String layerProvinciesWfs =
      "/app/default/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied";
  String layerBegroeidTerreindeelPostgis =
      "/app/default/layer/lyr:snapshot-geoserver:postgis:begroeidterreindeel";
  String layerWaterdeelOracle = "/app/default/layer/lyr:snapshot-geoserver:oracle:WATERDEEL";
  String layerWegdeelSqlServer = "/app/default/layer/lyr:snapshot-geoserver:sqlserver:wegdeel";
  String layerOsmPolygonPostgis = "/app/default/layer/lyr:snapshot-geoserver:postgis:osm_polygon";
  String layerProxiedWithAuthInPublicApp =
      "/app/default/layer/lyr:bestuurlijkegebieden-proxied:Provinciegebied";
  String layerWaterdeel = "/app/default/layer/lyr:snapshot-geoserver:oracle:WATERDEEL";
}
