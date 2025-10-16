/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.persistence.helper;

import static java.util.Objects.requireNonNullElse;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.json.AppI18nSettings;
import org.tailormap.api.persistence.json.AppUiSettings;
import org.tailormap.api.persistence.json.AttributeSettings;
import org.tailormap.api.persistence.json.Filter;
import org.tailormap.api.persistence.json.FilterGroup;
import org.tailormap.api.security.AuthorisationService;
import org.tailormap.api.viewer.model.ViewerResponse;

@Service
public class ViewerResponseHelper {
  private final AuthorisationService authorisationService;
  private final ViewerHelper viewerHelper;

  public ViewerResponseHelper(AuthorisationService authorisationService, ViewerHelper viewerHelper) {
    this.authorisationService = authorisationService;
    this.viewerHelper = viewerHelper;
  }

  @Transactional
  public ViewerResponse getViewerResponse(Application application) {
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
        .filterGroups(verifyFilterGroups(application));
  }

  private List<FilterGroup> verifyFilterGroups(Application application) {

    List<FilterGroup> filterGroups = application.getSettings().getFilterGroups();

    Map<String, ViewerHelper.AppLayerFullContext> appLayerFullContextMap = viewerHelper.getAppLayerFullContextMap(
        application,
        filterGroups.stream()
            .map(FilterGroup::getLayerIds)
            .flatMap(List::stream)
            .toList());

    removeUnauthorizedLayersFromFilterGroups(appLayerFullContextMap, filterGroups);

    removeLayersFromFilterGroupsWithoutFeatureType(appLayerFullContextMap, filterGroups);

    removeFiltersWithoutAttributeInAllLayers(appLayerFullContextMap, filterGroups);

    removeEmptyFilterGroups(filterGroups);

    setAttributeAliasInFilters(appLayerFullContextMap, filterGroups);

    return filterGroups;
  }

  private void removeUnauthorizedLayersFromFilterGroups(
      Map<String, ViewerHelper.AppLayerFullContext> appLayerFullContextMap, List<FilterGroup> filterGroups) {
    for (FilterGroup filterGroup : filterGroups) {
      filterGroup.setLayerIds(filterGroup.getLayerIds().stream()
          .filter(appLayerId -> isAppLayerAuthorized(appLayerFullContextMap.get(appLayerId)))
          .toList());
    }
  }

  private boolean isAppLayerAuthorized(ViewerHelper.AppLayerFullContext appLayerContext) {
    return appLayerContext != null
        && authorisationService.userAllowedToViewGeoService(appLayerContext.geoService())
        && authorisationService.userAllowedToViewGeoServiceLayer(
            appLayerContext.geoService(), appLayerContext.geoServiceLayer());
  }

  private void removeLayersFromFilterGroupsWithoutFeatureType(
      Map<String, ViewerHelper.AppLayerFullContext> appLayerFullContextMap, List<FilterGroup> filterGroups) {
    for (FilterGroup filterGroup : filterGroups) {
      filterGroup.setLayerIds(filterGroup.getLayerIds().stream()
          .filter(appLayerFullContextMap::containsKey)
          .toList());
    }
  }

  private void removeFiltersWithoutAttributeInAllLayers(
      Map<String, ViewerHelper.AppLayerFullContext> appLayerFullContextMap, List<FilterGroup> filterGroups) {

    for (FilterGroup filterGroup : filterGroups) {
      Set<String> attributesCommonToAllLayers = filterGroup.getLayerIds().stream()
          .map(appLayerFullContextMap::get)
          .map(appLayerContext -> TMFeatureTypeHelper.getNonHiddenAttributeNames(
              appLayerContext.featureType(), appLayerContext.appLayerSettings()))
          .reduce((a, b) -> {
            Set<String> intersection = new HashSet<>(a);
            intersection.retainAll(b);
            return intersection;
          })
          .orElse(new HashSet<>());

      filterGroup.setFilters(filterGroup.getFilters().stream()
          .filter(f -> attributesCommonToAllLayers.contains(f.getAttribute()))
          .toList());
    }
  }

  private void removeEmptyFilterGroups(List<FilterGroup> filterGroups) {
    filterGroups.removeIf(
        fg -> fg.getLayerIds().isEmpty() || fg.getFilters().isEmpty());
  }

  private void setAttributeAliasInFilters(
      Map<String, ViewerHelper.AppLayerFullContext> appLayerFullContextMap, List<FilterGroup> filterGroups) {
    for (FilterGroup filterGroup : filterGroups) {
      for (Filter filter : filterGroup.getFilters()) {
        filterGroup.getLayerIds().stream()
            .map(appLayerFullContextMap::get)
            .map(appLayerContext -> appLayerContext
                .featureType()
                .getSettings()
                .getAttributeSettings()
                .get(filter.getAttribute()))
            .map(AttributeSettings::getTitle)
            .filter(Objects::nonNull)
            .findFirst()
            .ifPresent(filter::setAttributeAlias);
      }
    }
  }
}
