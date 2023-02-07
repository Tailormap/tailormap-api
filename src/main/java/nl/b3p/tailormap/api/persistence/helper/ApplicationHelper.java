/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence.helper;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.json.AppContent;
import nl.b3p.tailormap.api.persistence.json.AppLayerRef;
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

    AppContent content = app.getContentRoot();

    LayerTreeNode root = new LayerTreeNode().id("root").root(true);
    mr.addLayerTreeNodesItem(root);
    int layerIdCounter = 0;
    Set<Long> addedServiceIds = new HashSet<>();

    for (AppLayerRef layerRef : content.getLayers()) {
      GeoService service = geoServiceRepository.findById(layerRef.getServiceId()).orElse(null);
      if (service == null) {
        log.warn(
            String.format(
                "App %d references layer \"%s\" of missing service %d",
                app.getId(), layerRef.getLayerName(), layerRef.getServiceId()));
        continue;
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
        continue;
      }

      if (!addedServiceIds.contains(service.getId())) {
        mr.addServicesItem(service.toJsonPojo());
        addedServiceIds.add(service.getId());
      }

      mr.addAppLayersItem(
          new AppLayer()
              .id((long) layerIdCounter) // XXX
              .hasAttributes(false)
              .layerName(layerRef.getLayerName())
              .opacity(100)
              // Can't set opaque, not mapped by GeoTools gt-wms?
              .maxScale(serviceLayer.getMaxScale())
              .minScale(serviceLayer.getMinScale())
              .serviceId(service.getId())
              .title(
                  // Evt Opzoeken in layerSettings, alleen bij service of ook per app?
                  layerRef.getLayerName())
              .visible(true));

      mr.addLayerTreeNodesItem(
          new LayerTreeNode()
              .id("lyr_" + layerIdCounter)
              .appLayerId(layerIdCounter)
              .description(serviceLayer.getAbstractText())
              .name(layerRef.getLayerName())
              .root(false));

      root.addChildrenIdsItem("lyr_" + layerIdCounter);
      layerIdCounter++;
    }
  }
}
