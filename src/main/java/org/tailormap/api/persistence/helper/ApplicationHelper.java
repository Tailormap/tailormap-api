/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence.helper;

import static java.util.stream.Collectors.toSet;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.tailormap.api.persistence.helper.GeoServiceHelper.getWmsRequest;
import static org.tailormap.api.persistence.json.GeoServiceProtocol.LEGEND;
import static org.tailormap.api.persistence.json.GeoServiceProtocol.QUANTIZEDMESH;
import static org.tailormap.api.persistence.json.GeoServiceProtocol.TILES3D;
import static org.tailormap.api.persistence.json.GeoServiceProtocol.XYZ;
import static org.tailormap.api.util.TMStringUtils.nullIfEmpty;

import jakarta.persistence.EntityManager;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.ObjectUtils;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.referencing.util.CRSUtilities;
import org.geotools.referencing.wkt.Formattable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tailormap.api.controller.GeoServiceProxyController;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.Configuration;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.SearchIndex;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.Upload;
import org.tailormap.api.persistence.json.AppContent;
import org.tailormap.api.persistence.json.AppLayerSettings;
import org.tailormap.api.persistence.json.AppTreeLayerNode;
import org.tailormap.api.persistence.json.AppTreeLevelNode;
import org.tailormap.api.persistence.json.AppTreeNode;
import org.tailormap.api.persistence.json.Bounds;
import org.tailormap.api.persistence.json.GeoServiceDefaultLayerSettings;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.persistence.json.GeoServiceLayerSettings;
import org.tailormap.api.persistence.json.ServicePublishingSettings;
import org.tailormap.api.persistence.json.TileLayerHiDpiMode;
import org.tailormap.api.repository.ApplicationRepository;
import org.tailormap.api.repository.ConfigurationRepository;
import org.tailormap.api.repository.FeatureSourceRepository;
import org.tailormap.api.repository.GeoServiceRepository;
import org.tailormap.api.repository.SearchIndexRepository;
import org.tailormap.api.security.AuthorisationService;
import org.tailormap.api.viewer.model.AppLayer;
import org.tailormap.api.viewer.model.LayerSearchIndex;
import org.tailormap.api.viewer.model.LayerTreeNode;
import org.tailormap.api.viewer.model.MapResponse;
import org.tailormap.api.viewer.model.TMCoordinateReferenceSystem;

@Service
public class ApplicationHelper {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private static final String DEFAULT_WEB_MERCATOR_CRS = "EPSG:3857";

  private final GeoServiceHelper geoServiceHelper;
  private final GeoServiceRepository geoServiceRepository;
  private final ConfigurationRepository configurationRepository;
  private final ApplicationRepository applicationRepository;
  private final FeatureSourceRepository featureSourceRepository;
  private final EntityManager entityManager;
  private final AuthorisationService authorisationService;
  private final SearchIndexRepository searchIndexRepository;
  private final UploadHelper uploadHelper;

  public ApplicationHelper(
      GeoServiceHelper geoServiceHelper,
      GeoServiceRepository geoServiceRepository,
      ConfigurationRepository configurationRepository,
      ApplicationRepository applicationRepository,
      FeatureSourceRepository featureSourceRepository,
      EntityManager entityManager,
      AuthorisationService authorisationService,
      SearchIndexRepository searchIndexRepository,
      UploadHelper uploadHelper) {
    this.geoServiceHelper = geoServiceHelper;
    this.geoServiceRepository = geoServiceRepository;
    this.configurationRepository = configurationRepository;
    this.applicationRepository = applicationRepository;
    this.featureSourceRepository = featureSourceRepository;
    this.entityManager = entityManager;
    this.authorisationService = authorisationService;
    this.searchIndexRepository = searchIndexRepository;
    this.uploadHelper = uploadHelper;
  }

  public Application getServiceApplication(String baseAppName, String projection, GeoService service) {
    if (baseAppName == null) {
      baseAppName = Optional.ofNullable(service.getSettings().getPublishing())
          .map(ServicePublishingSettings::getBaseApp)
          .orElseGet(() -> configurationRepository.get(Configuration.DEFAULT_BASE_APP));
    }

    Application baseApp = null;
    if (baseAppName != null) {
      baseApp = applicationRepository.findByName(baseAppName);
      if (baseApp != null) {
        // Caller may be changing the app content to add layers from this service, detach so those
        // aren't saved
        entityManager.detach(baseApp);
      }
    }

    Application app = baseApp != null ? baseApp : new Application().setContentRoot(new AppContent());

    if (projection != null) {
      // TODO: filter layers by projection parameter (layer.crs must inherit crs from parent layers)
      throw new UnsupportedOperationException("Projection filtering not yet supported");
    } else {
      if (baseApp != null) {
        projection = baseApp.getCrs();
      } else {
        projection = DEFAULT_WEB_MERCATOR_CRS;
      }
    }

    app.setName(service.getId()).setTitle(service.getTitle()).setCrs(projection);

    return app;
  }

  @Transactional
  public MapResponse toMapResponse(Application app) {
    MapResponse mapResponse = new MapResponse();
    setCrsAndBounds(app, mapResponse);
    setLayers(app, mapResponse);
    return mapResponse;
  }

  public void setCrsAndBounds(Application a, MapResponse mapResponse) {
    CoordinateReferenceSystem gtCrs = a.getGeoToolsCoordinateReferenceSystem();
    if (gtCrs == null) {
      throw new IllegalArgumentException("Invalid CRS: " + a.getCrs());
    }

    TMCoordinateReferenceSystem crs = new TMCoordinateReferenceSystem()
        .code(a.getCrs())
        .definition(((Formattable) gtCrs).toWKT(0))
        .bounds(GeoToolsHelper.fromCRS(gtCrs))
        .unit(Optional.ofNullable(CRSUtilities.getUnit(gtCrs.getCoordinateSystem()))
            .map(Objects::toString)
            .orElse(null));

    Bounds maxExtent = a.getMaxExtent() != null ? a.getMaxExtent() : crs.getBounds();
    Bounds initialExtent = a.getInitialExtent() != null ? a.getInitialExtent() : maxExtent;

    mapResponse.crs(crs).maxExtent(maxExtent).initialExtent(initialExtent);
  }

  private void setLayers(Application app, MapResponse mr) {
    new MapResponseLayerBuilder(app, mr).buildLayers();
  }

  private String getProxyUrl(GeoService geoService, Application application, AppTreeLayerNode appTreeLayerNode) {
    String baseProxyUrl = linkTo(
            GeoServiceProxyController.class,
            Map.of(
                "viewerKind", "app", // XXX
                "viewerName", application.getName(),
                "appLayerId", appTreeLayerNode.getId()))
        .toString();

    String protocolPath = "/" + geoService.getProtocol().getValue();

    if (geoService.getProtocol() == TILES3D) {
      return baseProxyUrl + protocolPath + "/" + GeoServiceProxyController.TILES3D_DESCRIPTION_PATH;
    }
    return baseProxyUrl + protocolPath;
  }

  private String getLegendProxyUrl(Application application, AppTreeLayerNode appTreeLayerNode) {
    return linkTo(
            GeoServiceProxyController.class,
            Map.of(
                "viewerKind",
                "app",
                "viewerName",
                application.getName(),
                "appLayerId",
                appTreeLayerNode.getId()))
        + "/" + LEGEND.getValue();
  }

  private class MapResponseLayerBuilder {
    private final Application app;
    private final MapResponse mapResponse;
    // XXX not needed if we have GeoServiceLayer.getService().getName()
    private final Map<GeoServiceLayer, String> serviceLayerServiceIds = new HashMap<>();

    MapResponseLayerBuilder(Application app, MapResponse mapResponse) {
      this.app = app;
      this.mapResponse = mapResponse;
    }

    void buildLayers() {
      if (app.getContentRoot() != null) {
        buildBackgroundLayers();
        buildOverlayLayers();
        buildTerrainLayers();
      }
    }

    private void buildBackgroundLayers() {
      if (app.getContentRoot().getBaseLayerNodes() != null) {
        for (AppTreeNode node : app.getContentRoot().getBaseLayerNodes()) {
          addAppTreeNodeItem(node, mapResponse.getBaseLayerTreeNodes());
        }

        Set<String> validLayerIds =
            mapResponse.getAppLayers().stream().map(AppLayer::getId).collect(toSet());
        List<LayerTreeNode> initialLayerTreeNodes = mapResponse.getBaseLayerTreeNodes();

        mapResponse.setBaseLayerTreeNodes(cleanLayerTreeNodes(validLayerIds, initialLayerTreeNodes));
      }
    }

    private void buildOverlayLayers() {
      if (app.getContentRoot().getLayerNodes() != null) {
        for (AppTreeNode node : app.getContentRoot().getLayerNodes()) {
          addAppTreeNodeItem(node, mapResponse.getLayerTreeNodes());
        }
        Set<String> validLayerIds =
            mapResponse.getAppLayers().stream().map(AppLayer::getId).collect(toSet());
        List<LayerTreeNode> initialLayerTreeNodes = mapResponse.getLayerTreeNodes();

        mapResponse.setLayerTreeNodes(cleanLayerTreeNodes(validLayerIds, initialLayerTreeNodes));
      }
    }

    /**
     * Cleans the layer tree nodes by removing references to non-existing layers and removing level nodes without
     * children, thus preventing exposure of application internals to unauthorized users.
     *
     * @param validLayerIds the ids of the layers that exist
     * @param initialLayerTreeNodes the initial list of layer tree nodes
     * @return the cleaned list of layer tree nodes
     */
    private List<LayerTreeNode> cleanLayerTreeNodes(
        Set<String> validLayerIds, List<LayerTreeNode> initialLayerTreeNodes) {
      List<String> levelNodes = initialLayerTreeNodes.stream()
          .filter(n -> n.getAppLayerId() == null)
          .map(LayerTreeNode::getId)
          .toList();

      List<LayerTreeNode> newLayerTreeNodes = initialLayerTreeNodes.stream()
          .peek(n -> {
            n.getChildrenIds()
                .removeIf(childId ->
                    /* remove invalid layers from the children */
                    !validLayerIds.contains(childId) && !levelNodes.contains(childId));
          })
          .filter(n ->
              /* remove level nodes without children */
              !(n.getAppLayerId() == null
                  && (n.getChildrenIds() != null
                      && n.getChildrenIds().isEmpty())))
          .toList();

      List<String> cleanLevelNodeIds = newLayerTreeNodes.stream()
          .filter(n -> n.getAppLayerId() == null)
          .map(LayerTreeNode::getId)
          .toList();

      return newLayerTreeNodes.stream()
          .peek(n -> {
            n.getChildrenIds()
                .removeIf(childId ->
                    /* remove invalid layers from the children */
                    !cleanLevelNodeIds.contains(childId) && levelNodes.contains(childId));
          })
          .toList();
    }

    private void buildTerrainLayers() {
      if (app.getContentRoot().getTerrainLayerNodes() != null) {
        for (AppTreeNode node : app.getContentRoot().getTerrainLayerNodes()) {
          addAppTreeNodeItem(node, mapResponse.getTerrainLayerTreeNodes());
        }
      }
    }

    private void addAppTreeNodeItem(AppTreeNode node, List<LayerTreeNode> layerTreeNodeList) {
      LayerTreeNode layerTreeNode = new LayerTreeNode();
      if ("AppTreeLayerNode".equals(node.getObjectType())) {
        AppTreeLayerNode appTreeLayerNode = (AppTreeLayerNode) node;
        layerTreeNode.setId(appTreeLayerNode.getId());
        layerTreeNode.setAppLayerId(appTreeLayerNode.getId());
        if (!addAppLayerItem(appTreeLayerNode)) {
          return;
        }
        // This name is not displayed in the frontend, the title from the appLayer node is used
        layerTreeNode.setName(appTreeLayerNode.getLayerName());
        layerTreeNode.setDescription(appTreeLayerNode.getDescription());
      } else if ("AppTreeLevelNode".equals(node.getObjectType())) {
        AppTreeLevelNode appTreeLevelNode = (AppTreeLevelNode) node;
        layerTreeNode.setId(appTreeLevelNode.getId());
        layerTreeNode.setChildrenIds(appTreeLevelNode.getChildrenIds());
        layerTreeNode.setRoot(Boolean.TRUE.equals(appTreeLevelNode.getRoot()));
        // The name for a level node does show in the frontend
        layerTreeNode.setName(appTreeLevelNode.getTitle());
        layerTreeNode.setDescription(appTreeLevelNode.getDescription());
      }
      layerTreeNodeList.add(layerTreeNode);
    }

    private boolean addAppLayerItem(AppTreeLayerNode layerRef) {
      ServiceLayerInfo layerInfo = findServiceLayer(layerRef);
      if (layerInfo == null) {
        return false;
      }
      GeoService service = layerInfo.service();
      GeoServiceLayer serviceLayer = layerInfo.serviceLayer();

      // Some settings can be set on the app layer level, layer level or service level (default
      // layer settings). These settings should be used in-order: from app layer if set, otherwise
      // from layer level, from default layer setting or the default.

      // An empty (blank) string means it is not set. To explicitly clear a layer level string
      // setting for an app, an admin should set the app layer setting to spaces.

      // The JSON wrapper classes have "null" values as defaults, which means not-set. The defaults
      // (such as tilingDisabled being true) are applied below, although the frontend would also
      // treat null as non-truthy.

      // When default layer settings or settings for a specific layer are missing, construct new
      // settings objects so no null check is needed. All properties are initialized to null
      // (not-set) by default.
      GeoServiceDefaultLayerSettings defaultLayerSettings = Optional.ofNullable(
              service.getSettings().getDefaultLayerSettings())
          .orElseGet(GeoServiceDefaultLayerSettings::new);
      GeoServiceLayerSettings serviceLayerSettings =
          Optional.ofNullable(layerInfo.layerSettings()).orElseGet(GeoServiceLayerSettings::new);

      AppLayerSettings appLayerSettings = app.getAppLayerSettings(layerRef);

      String title = Objects.requireNonNullElse(
          nullIfEmpty(appLayerSettings.getTitle()),
          // Get title from layer settings, title from capabilities or the layer name -- never
          // null
          service.getTitleWithSettingsOverrides(layerRef.getLayerName()));

      // These settings can be overridden per appLayer

      String description = ObjectUtils.firstNonNull(
          nullIfEmpty(appLayerSettings.getDescription()),
          nullIfEmpty(serviceLayerSettings.getDescription()),
          nullIfEmpty(defaultLayerSettings.getDescription()));

      String attribution = ObjectUtils.firstNonNull(
          nullIfEmpty(appLayerSettings.getAttribution()),
          nullIfEmpty(serviceLayerSettings.getAttribution()),
          nullIfEmpty(defaultLayerSettings.getAttribution()));

      // These settings can't be overridden per appLayer but can be set on a per-layer and
      // service-level default basis

      boolean tilingDisabled = ObjectUtils.firstNonNull(
          serviceLayerSettings.getTilingDisabled(), defaultLayerSettings.getTilingDisabled(), true);
      Integer tilingGutter = ObjectUtils.firstNonNull(
          serviceLayerSettings.getTilingGutter(), defaultLayerSettings.getTilingGutter(), 0);
      boolean hiDpiDisabled = ObjectUtils.firstNonNull(
          serviceLayerSettings.getHiDpiDisabled(), defaultLayerSettings.getHiDpiDisabled(), true);
      TileLayerHiDpiMode hiDpiMode = ObjectUtils.firstNonNull(
          serviceLayerSettings.getHiDpiMode(), defaultLayerSettings.getHiDpiMode(), null);
      // Do not get from defaultLayerSettings because a default wouldn't make sense
      String hiDpiSubstituteLayer = serviceLayerSettings.getHiDpiSubstituteLayer();

      TMFeatureType tmft = service.findFeatureTypeForLayer(serviceLayer, featureSourceRepository);

      boolean proxied = service.getSettings().getUseProxy();

      String legendImageUrl = serviceLayerSettings.getLegendImageId();
      AppLayer.LegendTypeEnum legendType = AppLayer.LegendTypeEnum.STATIC;

      if (legendImageUrl == null && serviceLayer.getStyles() != null) {
        // no user defined legend image, try to get legend image from styles
        legendImageUrl = Optional.ofNullable(
                GeoServiceHelper.getLayerLegendUrlFromStyles(service, serviceLayer))
            .map(URI::toString)
            .orElse(null);

        if (legendImageUrl != null) {
          // Check whether the legend is dynamic or static based on the original legend URL, before it is
          // possibly replaced by URL to the proxy controller
          legendType = "GetLegendGraphic".equalsIgnoreCase(getWmsRequest(legendImageUrl))
              ? AppLayer.LegendTypeEnum.DYNAMIC
              : AppLayer.LegendTypeEnum.STATIC;

          if (proxied) {
            // service styles provides a legend image, but we need to proxy it
            legendImageUrl = getLegendProxyUrl(app, layerRef);
          }
        }
      }

      SearchIndex searchIndex = null;
      if (appLayerSettings.getSearchIndexId() != null) {
        searchIndex = searchIndexRepository
            .findById(appLayerSettings.getSearchIndexId())
            .orElse(null);
      }

      boolean webMercatorAvailable = this.isWebMercatorAvailable(service, serviceLayer, hiDpiSubstituteLayer);

      String tileset3dStyleUrl = null;
      if (service.getProtocol() == TILES3D && appLayerSettings.getTileset3dStyleId() != null) {
        tileset3dStyleUrl = uploadHelper.getUrlForImage(
            appLayerSettings.getTileset3dStyleId(), Upload.CATEGORY_TILESET_3D_STYLE);
      }

      mapResponse.addAppLayersItem(new AppLayer()
          .id(layerRef.getId())
          .serviceId(serviceLayerServiceIds.get(serviceLayer))
          .layerName(layerRef.getLayerName())
          .hasAttributes(tmft != null)
          .editable(TMFeatureTypeHelper.isEditable(app, layerRef, tmft))
          .url(proxied ? getProxyUrl(service, app, layerRef) : null)
          // Can't set whether layer is opaque, not mapped from WMS capabilities by GeoTools
          // gt-wms Layer class?
          .maxScale(serviceLayer.getMaxScale())
          .minScale(serviceLayer.getMinScale())
          .title(title)
          .tilingDisabled(tilingDisabled)
          .tilingGutter(tilingGutter)
          .hiDpiDisabled(hiDpiDisabled)
          .hiDpiMode(hiDpiMode)
          .hiDpiSubstituteLayer(hiDpiSubstituteLayer)
          .minZoom(serviceLayerSettings.getMinZoom())
          .maxZoom(serviceLayerSettings.getMaxZoom())
          .tileSize(serviceLayerSettings.getTileSize())
          .tileGridExtent(serviceLayerSettings.getTileGridExtent())
          .opacity(appLayerSettings.getOpacity())
          .autoRefreshInSeconds(appLayerSettings.getAutoRefreshInSeconds())
          .searchIndex(
              searchIndex != null
                  ? new LayerSearchIndex()
                      .id(searchIndex.getId())
                      .name(searchIndex.getName())
                  : null)
          .legendImageUrl(legendImageUrl)
          .legendType(legendType)
          .visible(layerRef.getVisible())
          .attribution(attribution)
          .description(description)
          .webMercatorAvailable(webMercatorAvailable)
          .tileset3dStyleUrl(tileset3dStyleUrl)
          .hiddenFunctionality(appLayerSettings.getHiddenFunctionality()));

      return true;
    }

    private ServiceLayerInfo findServiceLayer(AppTreeLayerNode layerRef) {
      GeoService service =
          geoServiceRepository.findById(layerRef.getServiceId()).orElse(null);
      if (service == null) {
        logger.warn(
            "App {} references layer \"{}\" of missing service {}",
            app.getId(),
            layerRef.getLayerName(),
            layerRef.getServiceId());
        return null;
      }

      if (authorisationService.mustDenyAccessForSecuredProxy(service)) {
        return null;
      }

      if (!authorisationService.userAllowedToViewGeoService(service)) {
        return null;
      }

      GeoServiceLayer serviceLayer = service.findLayer(layerRef.getLayerName());

      if (serviceLayer == null) {
        logger.warn(
            "App {} references layer \"{}\" not found in capabilities of service {}",
            app.getId(),
            layerRef.getLayerName(),
            service.getId());
        return null;
      }

      if (!authorisationService.userAllowedToViewGeoServiceLayer(service, serviceLayer)) {
        logger.debug(
            "User not allowed to view layer {} of service {}", serviceLayer.getName(), service.getId());
        return null;
      }

      serviceLayerServiceIds.put(serviceLayer, service.getId());

      if (mapResponse.getServices().stream()
          .filter(s -> s.getId().equals(service.getId()))
          .findAny()
          .isEmpty()) {
        mapResponse.addServicesItem(service.toJsonPojo(geoServiceHelper));
      }

      GeoServiceLayerSettings layerSettings = service.getLayerSettings(layerRef.getLayerName());
      return new ServiceLayerInfo(service, serviceLayer, layerSettings);
    }

    private boolean isWebMercatorAvailable(
        GeoService service, GeoServiceLayer serviceLayer, String hiDpiSubstituteLayer) {
      if (service.getProtocol() == XYZ) {
        return DEFAULT_WEB_MERCATOR_CRS.equals(service.getSettings().getXyzCrs());
      }
      if (service.getProtocol() == TILES3D || service.getProtocol() == QUANTIZEDMESH) {
        return false;
      }
      if (hiDpiSubstituteLayer != null) {
        GeoServiceLayer hiDpiSubstituteServiceLayer = service.findLayer(hiDpiSubstituteLayer);
        if (hiDpiSubstituteServiceLayer != null
            && !this.isWebMercatorAvailable(service, hiDpiSubstituteServiceLayer, null)) {
          return false;
        }
      }
      while (serviceLayer != null) {
        Set<String> layerCrs = serviceLayer.getCrs();
        if (layerCrs.contains(DEFAULT_WEB_MERCATOR_CRS)) {
          return true;
        }
        if (serviceLayer.getRoot()) {
          break;
        }
        serviceLayer = service.getParentLayer(serviceLayer.getId());
      }
      return false;
    }

    record ServiceLayerInfo(
        GeoService service, GeoServiceLayer serviceLayer, GeoServiceLayerSettings layerSettings) {}
  }
}
