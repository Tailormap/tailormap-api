/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence.helper;

import static java.util.Objects.requireNonNullElse;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.TMFeatureSource;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.json.AppI18nSettings;
import org.tailormap.api.persistence.json.AppTreeLayerNode;
import org.tailormap.api.persistence.json.AppUiSettings;
import org.tailormap.api.persistence.json.AttributeSettings;
import org.tailormap.api.persistence.json.FeatureTypeRef;
import org.tailormap.api.persistence.json.Filter;
import org.tailormap.api.persistence.json.FilterGroup;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.persistence.json.TMAttributeDescriptor;
import org.tailormap.api.repository.FeatureSourceRepository;
import org.tailormap.api.repository.GeoServiceRepository;
import org.tailormap.api.security.AuthorisationService;
import org.tailormap.api.viewer.model.ViewerResponse;

@Service
public class ViewerHelper {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final GeoServiceRepository geoServiceRepository;
  private final AuthorisationService authorisationService;
  private final FeatureSourceRepository featureSourceRepository;

  public ViewerHelper(
      GeoServiceRepository geoServiceRepository,
      AuthorisationService authorisationService,
      FeatureSourceRepository featureSourceRepository) {
    this.geoServiceRepository = geoServiceRepository;
    this.authorisationService = authorisationService;
    this.featureSourceRepository = featureSourceRepository;
  }

  @Transactional
  public ViewerResponse getViewerResponse(Application application) {
    Map<String, FeatureTypeRef> appLayerIdToFeatureTypeId = new java.util.HashMap<>();
    //  filter the filterGroups to only have filterGroups with layerIds
    //  that are allowed for this user.
    List<FilterGroup> allowedFilterGroups = application.getSettings().getFilterGroups().stream()
        // for each filterGroup, filter if the layerIds are allowed
        .filter(fg -> {
          List<String> allowedLayerIds = new ArrayList<>();
          // for each layerId, get the service and layer, then check if viewing is allowed
          for (String layerId : fg.getLayerIds()) {
            AppTreeLayerNode lyrNode = application
                .getAllAppTreeLayerNode()
                .filter(node -> node.getId().equals(layerId))
                .findFirst()
                .orElse(null);
            if (lyrNode == null) {
              continue;
            }

            GeoService service = geoServiceRepository
                .findById(lyrNode.getServiceId())
                .orElse(null);
            if (service == null) {
              continue;
            }

            GeoServiceLayer layer = service.getLayers().stream()
                .filter(l -> Objects.equals(l.getName(), lyrNode.getLayerName()))
                .findFirst()
                .orElse(null);
            if (layer != null && authorisationService.userAllowedToViewGeoServiceLayer(service, layer)) {
              allowedLayerIds.add(layerId);
              appLayerIdToFeatureTypeId.put(
                  layerId,
                  service.getLayerSettings(layer.getName()).getFeatureType());
            }
          }

          if (allowedLayerIds.isEmpty()) {
            // no allowed layers in this filterGroup, skip the entire filterGroup
            logger.trace(
                "Skipping filterGroup {} because there are no allowed layers for this user",
                fg.getId());
            return false;
          } else {
            fg.setLayerIds(allowedLayerIds);
            return true;
          }
        })
        .toList();

    List<TMFeatureSource> featureSources =
        featureSourceRepository.findByIds(appLayerIdToFeatureTypeId.values().stream()
            .filter(Objects::nonNull)
            .map(FeatureTypeRef::getFeatureSourceId)
            .distinct()
            .toList());

    List<FilterGroup> verifiedFilterGroups = allowedFilterGroups.stream()
        .peek(fg -> {
          List<TMFeatureType> tmfts = new ArrayList<>();
          for (String layerId : fg.getLayerIds()) {
            FeatureTypeRef ftr = appLayerIdToFeatureTypeId.get(layerId);
            if (ftr == null) {
              continue;
            }
            TMFeatureSource tmfs = featureSources.stream()
                .filter(fs -> Objects.equals(fs.getId(), ftr.getFeatureSourceId()))
                .findFirst()
                .orElse(null);
            if (tmfs == null) {
              continue;
            }
            TMFeatureType tmft = tmfs.findFeatureTypeByName(ftr.getFeatureTypeName());
            if (tmft != null) {
              tmfts.add(tmft);
            }
          }
          List<Filter> verifiedFilters = this.verifyFilters(tmfts, fg.getFilters());
          fg.setFilters(verifiedFilters);
        })
        .toList();

    return new ViewerResponse()
        .kind(ViewerResponse.KindEnum.APP)
        .name(application.getName())
        .title(application.getTitle())
        .styling(application.getStyling())
        .components(application.getComponents())
        .i18nSettings(requireNonNullElse(
            application.getSettings().getI18nSettings(), new AppI18nSettings().hideLanguageSwitcher(false)))
        .uiSettings(requireNonNullElse(
            application.getSettings().getUiSettings(), new AppUiSettings().hideLoginButton(false)))
        .projections(List.of(application.getCrs()))
        .filterGroups(verifiedFilterGroups);
  }

  public List<Filter> verifyFilters(List<TMFeatureType> tmfts, List<Filter> filters) {
    if (filters == null) {
      return List.of();
    }
    List<TMAttributeDescriptor> sharedAttributes = new ArrayList<>();
    for (TMFeatureType tmft : tmfts) {
      if (sharedAttributes.isEmpty()) {
        sharedAttributes.addAll(tmft.getAttributes());
      } else {
        sharedAttributes.retainAll(tmft.getAttributes());
      }
    }

    List<Filter> verifiedFilters = new ArrayList<>();
    for (Filter filter : filters) {
      Optional<TMAttributeDescriptor> attr = sharedAttributes.stream()
          .filter(a -> a.getName().equals(filter.getAttribute()))
          .findFirst();
      if (attr.isPresent()) {
        AttributeSettings attributeSettings = tmfts.get(0).getSettings().getAttributeSettings().get(attr.get().getName());
        if (attributeSettings != null && attributeSettings.getTitle() != null) {
          filter.setAttributeAlias(attributeSettings.getTitle());
        }
        verifiedFilters.add(filter);
      }
    }
    return verifiedFilters;
  }
}
