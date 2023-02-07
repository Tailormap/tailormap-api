/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence.helper;

import nl.b3p.tailormap.api.viewer.model.Bounds;
import org.geotools.referencing.CRS;
import org.opengis.geometry.Envelope;

public class BoundsHelper {
  public static Bounds fromCRSEnvelope(org.opengis.referencing.crs.CoordinateReferenceSystem crs) {
    Envelope envelope = CRS.getEnvelope(crs);
    return envelope == null
        ? null
        : new Bounds()
            // ordinate choice may not always be correct...eg. with flipped axis
            .maxx(envelope.getUpperCorner().getOrdinate(0))
            .maxy(envelope.getUpperCorner().getOrdinate(1))
            .minx(envelope.getLowerCorner().getOrdinate(0))
            .miny(envelope.getLowerCorner().getOrdinate(1));
  }
}
