/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence.helper;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
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

  public record AppLayerContext(
      @NotNull AppTreeLayerNode node,
      @NotNull AppLayerSettings appLayerSettings,
      @NotNull GeoService geoService,
      @NotNull GeoServiceLayer geoServiceLayer,
      @Null FeatureTypeRef featureTypeRef) {}

  /**
   * Returns a map of AppTreeLayerNode id to AppLayerContext, with only nodes that reference an existing
   * GeoServiceLayer, including base layer nodes.
   *
   * @param application the application
   * @return a map of appLayerId to AppLayerContext
   */
  public Map<String, AppLayerContext> getAppLayerContextMap(Application application) {
    return getAppLayerContextMap(application, null);
  }

  /**
   * As #getAppLayerContextMap(), but only for the given appLayerIds.
   *
   * @param application the application
   * @param appLayerIds the list of appLayerIds to limit the result to
   * @return a map of appLayerId to AppLayerContext
   */
  public Map<String, AppLayerContext> getAppLayerContextMap(Application application, List<String> appLayerIds) {
    Map<String, AppTreeLayerNode> nodeMap = new HashMap<>();
    application.getAllAppTreeLayerNode().forEach(node -> {
      if (appLayerIds == null || appLayerIds.contains(node.getId())) {
        nodeMap.put(node.getId(), node);
      }
    });

    // Efficiently retrieve all GeoServices in a single query
    Map<String, GeoService> geoServiceMap =
        geoServiceRepository
            .findByIds(nodeMap.values().stream()
                .map(AppTreeLayerNode::getServiceId)
                .distinct()
                .toList())
            .stream()
            .collect(Collectors.toMap(GeoService::getId, service -> service));

    Map<String, AppLayerContext> contextMap = new HashMap<>();
    for (Map.Entry<String, AppTreeLayerNode> entry : nodeMap.entrySet()) {
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
      contextMap.put(
          appLayerId,
          new AppLayerContext(lyrNode, application.getAppLayerSettings(appLayerId), service, layer, ftr));
    }
    return contextMap;
  }

  public record AppLayerFullContext(
      @NotNull AppTreeLayerNode node,
      @NotNull AppLayerSettings appLayerSettings,
      @NotNull GeoService geoService,
      @NotNull GeoServiceLayer geoServiceLayer,
      @NotNull TMFeatureType featureType) {}

  /**
   * As #getAppLayerContextMap(), but only with nodes that reference an existing GeoServiceLayer that has a feature
   * type.
   *
   * @param application the application
   * @return a map of appLayerId to AppLayerFullContext
   */
  public Map<String, AppLayerFullContext> getAppLayerFullContextMap(Application application) {
    return getAppLayerFullContextMap(application, null);
  }

  /**
   * As #getAppLayerFullContextMap(), but only for the given appLayerIds.
   *
   * @param application the application
   * @param appLayerIds the list of appLayerIds to limit the result to
   * @return a map of appLayerId to AppLayerFullContext
   */
  public Map<String, AppLayerFullContext> getAppLayerFullContextMap(
      Application application, List<String> appLayerIds) {
    Map<String, AppLayerContext> contextMap = getAppLayerContextMap(application, appLayerIds);

    // Efficiently retrieve all TMFeatureSources in a single query
    Map<Long, TMFeatureSource> featureSourceMap = featureSourceRepository
        .findByIds(contextMap.values().stream()
            .map(appLayerFeatureTypeRef ->
                appLayerFeatureTypeRef.featureTypeRef().getFeatureSourceId())
            .filter(Objects::nonNull)
            .distinct()
            .toList())
        .stream()
        .collect(Collectors.toMap(TMFeatureSource::getId, featureSource -> featureSource));

    Map<String, AppLayerFullContext> fullContextMap = new HashMap<>();
    for (Map.Entry<String, AppLayerContext> entry : contextMap.entrySet()) {
      String appLayerId = entry.getKey();
      AppLayerContext context = entry.getValue();

      TMFeatureType featureType = Optional.ofNullable(
              featureSourceMap.get(context.featureTypeRef().getFeatureSourceId()))
          .map(featureSource -> featureSource.findFeatureTypeByName(
              context.featureTypeRef().getFeatureTypeName()))
          .orElse(null);

      if (featureType != null) {
        fullContextMap.put(
            appLayerId,
            new AppLayerFullContext(
                context.node(),
                context.appLayerSettings(),
                context.geoService(),
                context.geoServiceLayer(),
                featureType));
      }
    }

    return fullContextMap;
  }
}
