/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence.helper;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.persistence.EntityManager;
import nl.b3p.tailormap.api.controller.GeoServiceProxyController;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.Configuration;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.persistence.json.AppContent;
import nl.b3p.tailormap.api.persistence.json.AppLayerRef;
import nl.b3p.tailormap.api.persistence.json.BaseLayerInner;
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
import nl.b3p.tailormap.api.viewer.model.AppLayer;
import nl.b3p.tailormap.api.viewer.model.LayerTreeNode;
import nl.b3p.tailormap.api.viewer.model.MapResponse;
import nl.b3p.tailormap.api.viewer.model.TMCoordinateReferenceSystem;
import org.apache.commons.lang3.tuple.Triple;
import org.geotools.referencing.util.CRSUtilities;
import org.geotools.referencing.wkt.Formattable;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

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

  public ApplicationHelper(
      GeoServiceHelper geoServiceHelper,
      GeoServiceRepository geoServiceRepository,
      ConfigurationRepository configurationRepository,
      ApplicationRepository applicationRepository,
      FeatureSourceRepository featureSourceRepository,
      EntityManager entityManager) {
    this.geoServiceHelper = geoServiceHelper;
    this.geoServiceRepository = geoServiceRepository;
    this.configurationRepository = configurationRepository;
    this.applicationRepository = applicationRepository;
    this.featureSourceRepository = featureSourceRepository;
    this.entityManager = entityManager;
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

    service.getLayers().stream()
        .filter(l -> !l.getVirtual())
        .forEach(
            l ->
                app.getContentRoot()
                    .addLayersItem(
                        new AppLayerRef()
                            .serviceId(service.getId())
                            .layerName(l.getName())
                            .title(l.getTitle())));
    app.assignAppLayerRefNames();

    return app;
  }

  @Transactional
  public MapResponse toMapResponse(Application app) {
    MapResponse mapResponse = new MapResponse(null, null, null, null, null, null);
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
        new TMCoordinateReferenceSystem(a.getCrs(), ((Formattable) gtCrs).toWKT(0))
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
      GeoService geoService, Application application, AppLayerRef appLayerRef) {
    return linkTo(
            GeoServiceProxyController.class,
            Map.of(
                "viewerKind", "app", // XXX
                "viewerName", application.getName(),
                "appLayerId", appLayerRef.getId(),
                "protocol", geoService.getProtocol().getValue()))
        .toString();
  }

  private class MapResponseLayerBuilder {
    private final Application app;
    private final MapResponse mr;
    // XXX not needed if we have GeoServiceLayer.getService().getName()
    private final Map<GeoServiceLayer, String> serviceLayerServiceIds = new HashMap<>();
    private int levelIdCounter = 0;

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
      LayerTreeNode backgroundRootNode =
          new LayerTreeNode("base-layer-root", "Base layers").root(true);
      mr.addBaseLayerTreeNodesItem(backgroundRootNode);

      if (app.getContentRoot().getBaseLayers() != null) {
        for (BaseLayerInner baseLayer : app.getContentRoot().getBaseLayers()) {
          LayerTreeNode backgroundNode =
              new LayerTreeNode(("lvl_" + levelIdCounter++), (baseLayer.getTitle())).root(false);

          for (AppLayerRef layerRef : baseLayer.getLayers()) {
            addAppLayerItem(layerRef, backgroundNode, mr.getBaseLayerTreeNodes());
          }
          if (!CollectionUtils.isEmpty(backgroundNode.getChildrenIds())) {
            backgroundRootNode.addChildrenIdsItem(backgroundNode.getId());
            mr.addBaseLayerTreeNodesItem(backgroundNode);
          }
        }
      }
    }

    private void buildOverlayLayers() {
      LayerTreeNode rootNode = new LayerTreeNode("root", "Overlays").root(true);
      mr.addLayerTreeNodesItem(rootNode);
      // TODO: just supporting layers at the root node for now
      if (app.getContentRoot().getLayers() != null) {
        for (AppLayerRef layerRef : app.getContentRoot().getLayers()) {
          addAppLayerItem(layerRef, rootNode, mr.getLayerTreeNodes());
        }
      }
    }

    private void addAppLayerItem(
        AppLayerRef layerRef, LayerTreeNode parent, List<LayerTreeNode> layerTreeNodeList) {
      Triple<GeoService, GeoServiceLayer, GeoServiceLayerSettings> serviceWithLayer =
          findServiceLayer(layerRef);
      GeoService service = serviceWithLayer.getLeft();
      GeoServiceLayer serviceLayer = serviceWithLayer.getMiddle();

      if (service == null || serviceLayer == null) {
        return;
      }
      GeoServiceDefaultLayerSettings defaultLayerSettings =
          Optional.ofNullable(service.getSettings().getDefaultLayerSettings())
              .orElseGet(GeoServiceDefaultLayerSettings::new);
      Optional<GeoServiceLayerSettings> serviceLayerSettings =
          Optional.ofNullable(serviceWithLayer.getRight());

      String title =
          Objects.requireNonNullElse(
              layerRef.getTitle(), service.getTitleWithDefaults(layerRef.getLayerName()));

      boolean tilingDisabled =
          serviceLayerSettings
              .map(GeoServiceLayerSettings::getTilingDisabled)
              .orElse(Optional.ofNullable(defaultLayerSettings.getTilingDisabled()).orElse(false));
      Integer tilingGutter =
          serviceLayerSettings
              .map(GeoServiceLayerSettings::getTilingGutter)
              .orElse(defaultLayerSettings.getTilingGutter());
      boolean hiDpiDisabled =
          serviceLayerSettings
              .map(GeoServiceLayerSettings::getHiDpiDisabled)
              .orElse(Optional.ofNullable(defaultLayerSettings.getTilingDisabled()).orElse(false));
      TileLayerHiDpiMode hiDpiMode =
          serviceLayerSettings
              .map(GeoServiceLayerSettings::getHiDpiMode)
              .orElse(defaultLayerSettings.getHiDpiMode());
      // Do not get from defaultLayerSettings
      String hiDpiSubstituteLayer =
          serviceLayerSettings.map(GeoServiceLayerSettings::getHiDpiSubstituteLayer).orElse(null);

      TMFeatureType tmft = service.findFeatureTypeForLayer(serviceLayer, featureSourceRepository);

      boolean proxied = service.getSettings().getUseProxy();

      mr.addAppLayersItem(
          new AppLayer(
                  (layerRef.getId()),
                  (serviceLayerServiceIds.get(serviceLayer)),
                  (layerRef.getLayerName()),
                  (title),
                  layerRef.getVisible(),
                  (tmft != null))
              .url(proxied ? getProxyUrl(service, app, layerRef) : null)
              // Can't set whether layer is opaque, not mapped from WMS capabilities by GeoTools
              // gt-wms Layer class?
              .maxScale(serviceLayer.getMaxScale())
              .minScale(serviceLayer.getMinScale())
              .tilingDisabled(tilingDisabled)
              .tilingGutter(tilingGutter)
              .hiDpiDisabled(hiDpiDisabled)
              .hiDpiMode(hiDpiMode)
              .hiDpiSubstituteLayer(hiDpiSubstituteLayer)
              .opacity(layerRef.getOpacity()));

      LayerTreeNode layerNode =
          new LayerTreeNode(("lyr_" + layerRef.getId()), title)
              .appLayerId(layerRef.getId())
              .description(serviceLayer.getAbstractText())
              .root(false);
      parent.addChildrenIdsItem(layerNode.getId());
      layerTreeNodeList.add(layerNode);
    }

    private Triple<GeoService, GeoServiceLayer, GeoServiceLayerSettings> findServiceLayer(
        AppLayerRef layerRef) {
      GeoService service = geoServiceRepository.findById(layerRef.getServiceId()).orElse(null);
      if (service == null) {
        logger.warn(
            "App {} references layer \"{}\" of missing service {}",
            app.getId(),
            layerRef.getLayerName(),
            layerRef.getServiceId());
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
