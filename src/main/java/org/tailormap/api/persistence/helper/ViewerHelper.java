/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence.helper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.TMFeatureSource;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.json.AppLayerSettings;
import org.tailormap.api.persistence.json.AppTreeLayerNode;
import org.tailormap.api.persistence.json.FeatureTypeRef;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.persistence.json.GeoServiceLayerSettings;
import org.tailormap.api.repository.FeatureSourceRepository;
import org.tailormap.api.repository.GeoServiceRepository;

@Service
public class ViewerHelper {
  private final GeoServiceRepository geoServiceRepository;
  private final FeatureSourceRepository featureSourceRepository;

  public ViewerHelper(GeoServiceRepository geoServiceRepository, FeatureSourceRepository featureSourceRepository) {
    this.geoServiceRepository = geoServiceRepository;
    this.featureSourceRepository = featureSourceRepository;
  }

  public record AppLayerFullContext(
      AppTreeLayerNode node,
      AppLayerSettings appLayerSettings,
      GeoService geoService,
      GeoServiceLayer geoServiceLayer,
      TMFeatureType featureType) {}

  public record AppLayerContext(
      AppTreeLayerNode node,
      AppLayerSettings appLayerSettings,
      GeoService geoService,
      GeoServiceLayer geoServiceLayer,
      FeatureTypeRef featureTypeRef) {}

  public Map<String, AppLayerContext> getAppLayerContextMap(Application application, List<String> appLayerIds) {
    Map<String, AppTreeLayerNode> appLayerIdToAppLayerNode = new HashMap<>();
    for (String appLayerId : appLayerIds) {
      application
          .getAllAppTreeLayerNode()
          .filter(node -> node.getId().equals(appLayerId))
          .findFirst()
          .ifPresent(node -> appLayerIdToAppLayerNode.put(appLayerId, node));
    }

    Map<String, GeoService> geoServiceMap = geoServiceRepository
        .findByIds(appLayerIdToAppLayerNode.values().stream()
            .map(AppTreeLayerNode::getServiceId)
            .distinct()
            .toList())
        .stream()
        .collect(Collectors.toMap(GeoService::getId, service -> service));

    Map<String, AppLayerContext> appLayerIdContextMap = new HashMap<>();
    for (Map.Entry<String, AppTreeLayerNode> entry : appLayerIdToAppLayerNode.entrySet()) {
      String appLayerId = entry.getKey();
      AppTreeLayerNode lyrNode = entry.getValue();

      GeoService service = geoServiceMap.get(lyrNode.getServiceId());
      if (service == null) {
        continue;
      }

      GeoServiceLayer layer = service.getLayers().stream()
          .filter(l -> Objects.equals(l.getName(), lyrNode.getLayerName()))
          .findFirst()
          .orElse(null);
      if (layer == null) {
        continue;
      }

      GeoServiceLayerSettings lyrSettings = service.getLayerSettings(lyrNode.getLayerName());
      if (lyrSettings == null) {
        continue;
      }
      FeatureTypeRef ftr = lyrSettings.getFeatureType();
      if (ftr == null) {
        continue;
      }
      appLayerIdContextMap.put(
          appLayerId,
          new AppLayerContext(lyrNode, application.getAppLayerSettings(appLayerId), service, layer, ftr));
    }
    return appLayerIdContextMap;
  }

  public Map<String, AppLayerFullContext> getAppLayerFullContextMap(
      Application application, List<String> appLayerIds) {
    Map<String, AppLayerContext> appLayerContextMap = getAppLayerContextMap(application, appLayerIds);

    Map<Long, TMFeatureSource> featureSourceMap = featureSourceRepository
        .findByIds(appLayerContextMap.values().stream()
            .map(appLayerFeatureTypeRef ->
                appLayerFeatureTypeRef.featureTypeRef().getFeatureSourceId())
            .filter(Objects::nonNull)
            .distinct()
            .toList())
        .stream()
        .collect(Collectors.toMap(TMFeatureSource::getId, featureSource -> featureSource));

    Map<String, AppLayerFullContext> appLayerFullContextMap = new HashMap<>();
    for (Map.Entry<String, AppLayerContext> entry : appLayerContextMap.entrySet()) {
      String appLayerId = entry.getKey();
      AppLayerContext context = entry.getValue();

      TMFeatureType featureType = Optional.ofNullable(
              featureSourceMap.get(context.featureTypeRef().getFeatureSourceId()))
          .map(featureSource -> featureSource.findFeatureTypeByName(
              context.featureTypeRef().getFeatureTypeName()))
          .orElse(null);

      if (featureType != null) {
        appLayerFullContextMap.put(
            appLayerId,
            new AppLayerFullContext(
                context.node(),
                context.appLayerSettings(),
                context.geoService(),
                context.geoServiceLayer(),
                featureType));
      }
    }

    return appLayerFullContextMap;
  }
}
