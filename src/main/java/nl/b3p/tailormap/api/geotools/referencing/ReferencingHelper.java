/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.geotools.referencing;

import nl.b3p.tailormap.api.viewer.model.Bounds;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.referencing.CRS;
import org.opengis.geometry.Envelope;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public final class ReferencingHelper {
  private static final Log logger = LogFactory.getLog(ReferencingHelper.class);

  /**
   * Try to get the envelope from the CRS.
   *
   * @param authorityCode epsg code
   * @return the bounds, possibly with unknown extents
   * @see CRS#getEnvelope(CoordinateReferenceSystem)
   */
  public static Bounds crsBoundsExtractor(String authorityCode) {
    try {
      final CoordinateReferenceSystem crs = CRS.decode(authorityCode);
      Envelope envelope = CRS.getEnvelope(crs);
      // envelope can be null
      if (null != envelope) {
        return new Bounds()
            .crs(authorityCode)
            // ordinate choice may not always be correct...eg. with flipped axis
            .maxx(envelope.getUpperCorner().getOrdinate(0))
            .maxy(envelope.getUpperCorner().getOrdinate(1))
            .minx(envelope.getLowerCorner().getOrdinate(0))
            .miny(envelope.getLowerCorner().getOrdinate(1));
      }
    } catch (FactoryException e) {
      logger.warn("Failed to decode CRS. " + e.getMessage(), e);
    }
    return new Bounds().crs(authorityCode);
  }
}
