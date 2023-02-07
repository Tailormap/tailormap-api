/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence.helper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.json.AppLayerRef;
import nl.b3p.tailormap.api.persistence.json.BaseLayerInner;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayer;
import nl.b3p.tailormap.api.repository.GeoServiceRepository;
import nl.b3p.tailormap.api.viewer.model.AppLayer;
import nl.b3p.tailormap.api.viewer.model.Bounds;
import nl.b3p.tailormap.api.viewer.model.CoordinateReferenceSystem;
import nl.b3p.tailormap.api.viewer.model.LayerTreeNode;
import nl.b3p.tailormap.api.viewer.model.MapResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.referencing.util.CRSUtilities;
import org.geotools.referencing.wkt.Formattable;
import org.springframework.stereotype.Service;

@Service
public class ApplicationHelper {
  private static final Log log = LogFactory.getLog(ApplicationHelper.class);

  private final GeoServiceRepository geoServiceRepository;

  public ApplicationHelper(GeoServiceRepository geoServiceRepository) {
    this.geoServiceRepository = geoServiceRepository;
  }

  public MapResponse toMapResponse(Application app) {
    MapResponse mapResponse = new MapResponse();
    setCrsAndBounds(app, mapResponse);
    setLayers(app, mapResponse);
    return mapResponse;
  }

  public void setCrsAndBounds(Application a, MapResponse mapResponse) {

    org.opengis.referencing.crs.CoordinateReferenceSystem gtCrs =
        a.getGeoToolsCoordinateReferenceSystem();

    if (gtCrs == null) {
      throw new IllegalArgumentException("Invalid CRS: " + a.getCrs());
    }

    CoordinateReferenceSystem crs =
        new CoordinateReferenceSystem()
            .code(a.getCrs())
            .definition(((Formattable) gtCrs).toWKT(0))
            .bounds(BoundsHelper.fromCRSEnvelope(gtCrs))
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

  private class MapResponseLayerBuilder {
    private int layerIdCounter = 0;
    private int levelIdCounter = 0;

    private final Application app;
    private final MapResponse mr;

    // XXX not needed if we have GeoServiceLayer.getService().getId()
    private final Map<GeoServiceLayer, Long> serviceLayerServiceIds = new HashMap<>();

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
          new LayerTreeNode().id("base-layer-root").name("Base layers").root(true);
      mr.addBaseLayerTreeNodesItem(backgroundRootNode);

      if (app.getContentRoot().getBaseLayers() != null) {
        for (BaseLayerInner baseLayer : app.getContentRoot().getBaseLayers()) {
          LayerTreeNode backgroundNode =
              new LayerTreeNode()
                  .id("lvl_" + levelIdCounter++)
                  .name(baseLayer.getTitle())
                  .root(false);

          for (AppLayerRef layerRef : baseLayer.getLayers()) {
            addAppLayerItem(layerRef, backgroundNode, mr.getBaseLayerTreeNodes());
          }
          if (!backgroundNode.getChildrenIds().isEmpty()) {
            backgroundRootNode.addChildrenIdsItem(backgroundNode.getId());
            mr.addBaseLayerTreeNodesItem(backgroundNode);
          }
        }
      }
    }

    private void buildOverlayLayers() {
      LayerTreeNode rootNode = new LayerTreeNode().id("root").name("Overlays").root(true);
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
      GeoServiceLayer serviceLayer = findServiceLayer(layerRef);
      if (serviceLayer != null) {
        String title =
            Objects.requireNonNullElse(
                layerRef.getTitle(),
                Objects.requireNonNullElse(serviceLayer.getTitle(), layerRef.getLayerName()));
        mr.addAppLayersItem(
            new AppLayer()
                // XXX id's must be from config, not generated -> use string identifiers instead
                .id((long) layerIdCounter)
                .hasAttributes(false)
                .serviceId(serviceLayerServiceIds.get(serviceLayer))
                .layerName(layerRef.getLayerName())
                // Can't set whether layer is opaque, not mapped from WMS capabilities by GeoTools
                // gt-wms Layer class?
                .maxScale(serviceLayer.getMaxScale())
                .minScale(serviceLayer.getMinScale())
                .title(title)
                .opacity(layerRef.getOpacity())
                .visible(layerRef.getVisible()));

        LayerTreeNode layerNode =
            new LayerTreeNode()
                .id("lyr_" + layerIdCounter)
                .appLayerId(layerIdCounter)
                .description(serviceLayer.getAbstractText())
                .name(title)
                .root(false);
        parent.addChildrenIdsItem(layerNode.getId());
        layerTreeNodeList.add(layerNode);
        layerIdCounter++;
      }
    }

    private GeoServiceLayer findServiceLayer(AppLayerRef layerRef) {
      GeoService service = geoServiceRepository.findById(layerRef.getServiceId()).orElse(null);
      if (service == null) {
        log.warn(
            String.format(
                "App %d references layer \"%s\" of missing service %d",
                app.getId(), layerRef.getLayerName(), layerRef.getServiceId()));
        return null;
      }
      GeoServiceLayer serviceLayer =
          service.getLayers().stream()
              .filter(sl -> layerRef.getLayerName().equals(sl.getName()))
              .findFirst()
              .orElse(null);

      if (serviceLayer == null) {
        log.warn(
            String.format(
                "App %d references layer \"%s\" not found in capabilities of service %d",
                app.getId(), layerRef.getLayerName(), service.getId()));
        return null;
      }

      serviceLayerServiceIds.put(serviceLayer, service.getId());

      if (mr.getServices().stream()
          .filter(s -> s.getId().equals(service.getId()))
          .findAny()
          .isEmpty()) {
        mr.addServicesItem(service.toJsonPojo());
      }
      return serviceLayer;
    }
  }
}
