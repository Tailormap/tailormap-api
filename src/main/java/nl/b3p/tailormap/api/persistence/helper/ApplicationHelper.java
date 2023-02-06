/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence.helper;

import java.util.Objects;
import java.util.Optional;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.viewer.model.Bounds;
import nl.b3p.tailormap.api.viewer.model.CoordinateReferenceSystem;
import nl.b3p.tailormap.api.viewer.model.MapResponse;
import org.geotools.referencing.util.CRSUtilities;
import org.geotools.referencing.wkt.Formattable;
import org.springframework.stereotype.Service;

@Service
public class ApplicationHelper {

  public MapResponse toMapResponse(Application app) {
    MapResponse mapResponse = new MapResponse();
    setCrsAndBounds(app, mapResponse);
    return mapResponse;
  }

  static void setCrsAndBounds(Application a, MapResponse mapResponse) {

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

    Bounds maxExtent = Objects.requireNonNullElse(a.getMaxExtent(), crs.getBounds());
    Bounds initialExtent = Objects.requireNonNullElse(a.getInitialExtent(), maxExtent);

    mapResponse.crs(crs).maxExtent(maxExtent).initialExtent(initialExtent);
  }
}
