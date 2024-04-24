/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence.helper;

import static nl.b3p.tailormap.api.util.TMStringUtils.nullIfEmpty;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

import jakarta.persistence.EntityManager;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import nl.b3p.tailormap.api.controller.GeoServiceProxyController;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.Configuration;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.persistence.json.AppContent;
import nl.b3p.tailormap.api.persistence.json.AppLayerSettings;
import nl.b3p.tailormap.api.persistence.json.AppTreeLayerNode;
import nl.b3p.tailormap.api.persistence.json.AppTreeLevelNode;
import nl.b3p.tailormap.api.persistence.json.AppTreeNode;
import nl.b3p.tailormap.api.persistence.json.Bounds;
import nl.b3p.tailormap.api.persistence.json.GeoServiceDefaultLayerSettings;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayer;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayerSettings;
import nl.b3p.tailormap.api.persistence.json.ServicePublishingSettings;
import nl.b3p.tailormap.api.persistence.json.TileLayerHiDpiMode;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.repository.ConfigurationRepository;
import nl.b3p.tailormap.api.repository.FeatureSourceRepository;
import nl.b3p.tailormap.api.repository.GeoServiceRepository;
import nl.b3p.tailormap.api.security.AuthorizationService;
import nl.b3p.tailormap.api.viewer.model.AppLayer;
import nl.b3p.tailormap.api.viewer.model.LayerTreeNode;
import nl.b3p.tailormap.api.viewer.model.MapResponse;
import nl.b3p.tailormap.api.viewer.model.TMCoordinateReferenceSystem;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.referencing.util.CRSUtilities;
import org.geotools.referencing.wkt.Formattable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
  private final AuthorizationService authorizationService;

  public ApplicationHelper(
      GeoServiceHelper geoServiceHelper,
      GeoServiceRepository geoServiceRepository,
      ConfigurationRepository configurationRepository,
      ApplicationRepository applicationRepository,
      FeatureSourceRepository featureSourceRepository,
      EntityManager entityManager,
      AuthorizationService authorizationService) {
    this.geoServiceHelper = geoServiceHelper;
    this.geoServiceRepository = geoServiceRepository;
    this.configurationRepository = configurationRepository;
    this.applicationRepository = applicationRepository;
    this.featureSourceRepository = featureSourceRepository;
    this.entityManager = entityManager;
    this.authorizationService = authorizationService;
  }

  public Application getServiceApplication(
      String baseAppName, String projection, GeoService service) {
    if (baseAppName == null) {
      baseAppName =
          Optional.ofNullable(service.getSettings().getPublishing())
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

    Application app =
        baseApp != null ? baseApp : new Application().setContentRoot(new AppContent());

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

    TMCoordinateReferenceSystem crs =
        new TMCoordinateReferenceSystem()
            .code(a.getCrs())
            .definition(((Formattable) gtCrs).toWKT(0))
            .bounds(GeoToolsHelper.fromCRS(gtCrs))
            .unit(
                Optional.ofNullable(CRSUtilities.getUnit(gtCrs.getCoordinateSystem()))
                    .map(Objects::toString)
                    .orElse(null));

    Bounds maxExtent = a.getMaxExtent() != null ? a.getMaxExtent() : crs.getBounds();
    Bounds initialExtent = a.getInitialExtent() != null ? a.getInitialExtent() : maxExtent;

    mapResponse.crs(crs).maxExtent(maxExtent).initialExtent(initialExtent);
  }

  public void setLayers(Application app, MapResponse mr) {
    new MapResponseLayerBuilder(app, mr).buildLayers();
  }

  private String getProxyUrl(
      GeoService geoService, Application application, AppTreeLayerNode appTreeLayerNode) {
    return linkTo(
            GeoServiceProxyController.class,
            Map.of(
                "viewerKind", "app", // XXX
                "viewerName", application.getName(),
                "appLayerId", appTreeLayerNode.getId(),
                "protocol", geoService.getProtocol().getValue()))
        .toString();
  }

  private class MapResponseLayerBuilder {
    private final Application app;
    private final MapResponse mr;
    // XXX not needed if we have GeoServiceLayer.getService().getName()
    private final Map<GeoServiceLayer, String> serviceLayerServiceIds = new HashMap<>();

    public MapResponseLayerBuilder(Application app, MapResponse mr) {
      this.app = app;
      this.mr = mr;
    }

    public void buildLayers() {
      if (app.getContentRoot() != null) {
        buildBackgroundLayers();
        buildOverlayLayers();
      }
    }

    private void buildBackgroundLayers() {
      if (app.getContentRoot().getBaseLayerNodes() != null) {
        for (AppTreeNode node : app.getContentRoot().getBaseLayerNodes()) {
          addAppTreeNodeItem(node, mr.getBaseLayerTreeNodes());
        }
      }
    }

    private void buildOverlayLayers() {
      if (app.getContentRoot().getLayerNodes() != null) {
        for (AppTreeNode node : app.getContentRoot().getLayerNodes()) {
          addAppTreeNodeItem(node, mr.getLayerTreeNodes());
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
      Triple<GeoService, GeoServiceLayer, GeoServiceLayerSettings> serviceWithLayer =
          findServiceLayer(layerRef);
      GeoService service = serviceWithLayer.getLeft();
      GeoServiceLayer serviceLayer = serviceWithLayer.getMiddle();

      if (service == null || serviceLayer == null) {
        return false;
      }

      // Some settings can be set on the app layer level, layer level or service level (default
      // layer settings). These settings should be used in-order: from app layer if set, otherwise
      // from layer level, from default layer setting or the default.

      // An empty (blank) string means it is not set. To explicitly clear a layer level string
      // setting for an app, an admin should set the app layer setting to a spaces.

      // The JSON wrapper classes have "null" values as defaults which means not-set. The defaults
      // (such as tilingDisabled being false) are applied below although the frontend would also
      // treat null as non-truthy.

      // When default layer settings or settings for a specific layer are missing, construct new
      // settings objects so no null check is needed. All properties are initialized to null
      // (not-set) by default.
      GeoServiceDefaultLayerSettings defaultLayerSettings =
          Optional.ofNullable(service.getSettings().getDefaultLayerSettings())
              .orElseGet(GeoServiceDefaultLayerSettings::new);
      GeoServiceLayerSettings serviceLayerSettings =
          Optional.ofNullable(serviceWithLayer.getRight()).orElseGet(GeoServiceLayerSettings::new);

      AppLayerSettings appLayerSettings = app.getAppLayerSettings(layerRef);

      String title =
          Objects.requireNonNullElse(
              nullIfEmpty(appLayerSettings.getTitle()),
              // Get title from layer settings, title from capabilities or the layer name -- never
              // null
              service.getTitleWithSettingsOverrides(layerRef.getLayerName()));

      // These settings can be overridden per appLayer

      String description =
          ObjectUtils.firstNonNull(
              nullIfEmpty(appLayerSettings.getDescription()),
              nullIfEmpty(serviceLayerSettings.getDescription()),
              nullIfEmpty(defaultLayerSettings.getDescription()));

      String attribution =
          ObjectUtils.firstNonNull(
              nullIfEmpty(appLayerSettings.getAttribution()),
              nullIfEmpty(serviceLayerSettings.getAttribution()),
              nullIfEmpty(defaultLayerSettings.getAttribution()));

      // These settings can't be overridden per appLayer but can be set on a per-layer and
      // service-level default basis

      boolean tilingDisabled =
          ObjectUtils.firstNonNull(
              serviceLayerSettings.getTilingDisabled(),
              defaultLayerSettings.getTilingDisabled(),
              false);
      Integer tilingGutter =
          ObjectUtils.firstNonNull(
              serviceLayerSettings.getTilingGutter(), defaultLayerSettings.getTilingGutter(), 0);
      boolean hiDpiDisabled =
          ObjectUtils.firstNonNull(
              serviceLayerSettings.getHiDpiDisabled(),
              defaultLayerSettings.getHiDpiDisabled(),
              false);
      TileLayerHiDpiMode hiDpiMode =
          ObjectUtils.firstNonNull(
              serviceLayerSettings.getHiDpiMode(), defaultLayerSettings.getHiDpiMode(), null);
      // Do not get from defaultLayerSettings because a default wouldn't make sense
      String hiDpiSubstituteLayer = serviceLayerSettings.getHiDpiSubstituteLayer();

      TMFeatureType tmft = service.findFeatureTypeForLayer(serviceLayer, featureSourceRepository);

      boolean proxied = service.getSettings().getUseProxy();

      mr.addAppLayersItem(
          new AppLayer()
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
              .visible(layerRef.getVisible())
              .attribution(attribution)
              .description(description));
      return true;
    }

    private Triple<GeoService, GeoServiceLayer, GeoServiceLayerSettings> findServiceLayer(
        AppTreeLayerNode layerRef) {
      GeoService service = geoServiceRepository.findById(layerRef.getServiceId()).orElse(null);
      if (service == null) {
        logger.warn(
            "App {} references layer \"{}\" of missing service {}",
            app.getId(),
            layerRef.getLayerName(),
            layerRef.getServiceId());
        return Triple.of(null, null, null);
      }

      if (!authorizationService.mayUserRead(service)) {
        return Triple.of(null, null, null);
      }

      GeoServiceLayer serviceLayer = service.findLayer(layerRef.getLayerName());

      if (serviceLayer == null) {
        logger.warn(
            "App {} references layer \"{}\" not found in capabilities of service {}",
            app.getId(),
            layerRef.getLayerName(),
            service.getId());
        return Triple.of(null, null, null);
      }

      if (!authorizationService.mayUserRead(service, serviceLayer)) {
        return Triple.of(null, null, null);
      }

      serviceLayerServiceIds.put(serviceLayer, service.getId());

      if (mr.getServices().stream()
          .filter(s -> s.getId().equals(service.getId()))
          .findAny()
          .isEmpty()) {
        mr.addServicesItem(service.toJsonPojo(geoServiceHelper));
      }

      GeoServiceLayerSettings layerSettings = service.getLayerSettings(layerRef.getLayerName());
      return Triple.of(service, serviceLayer, layerSettings);
    }
  }
}
