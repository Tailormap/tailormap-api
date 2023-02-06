/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import java.util.Objects;
import javax.persistence.Embeddable;
import nl.b3p.tailormap.api.viewer.model.Bounds;
import org.geotools.referencing.CRS;
import org.opengis.geometry.Envelope;

@Embeddable
public class BoundingBox {
  String crs;

  Double minx, miny, maxx, maxy;

  // <editor-fold desc="getters and setters">
  public String getCrs() {
    return crs;
  }

  public BoundingBox setCrs(String crs) {
    this.crs = crs;
    return this;
  }

  public Double getMinx() {
    return minx;
  }

  public BoundingBox setMinx(Double minx) {
    this.minx = minx;
    return this;
  }

  public Double getMiny() {
    return miny;
  }

  public BoundingBox setMiny(Double miny) {
    this.miny = miny;
    return this;
  }

  public Double getMaxx() {
    return maxx;
  }

  public BoundingBox setMaxx(Double maxx) {
    this.maxx = maxx;
    return this;
  }

  public Double getMaxy() {
    return maxy;
  }

  public BoundingBox setMaxy(Double maxy) {
    this.maxy = maxy;
    return this;
  }
  // </editor-fold>

  public static BoundingBox fromCRSEnvelope(
      org.opengis.referencing.crs.CoordinateReferenceSystem crs) {
    Envelope envelope = CRS.getEnvelope(crs);
    return envelope == null
        ? null
        : new BoundingBox()
            .setCrs(crs.getIdentifiers().stream().findFirst().map(Objects::toString).orElse(null))
            // ordinate choice may not always be correct...eg. with flipped axis
            .setMaxx(envelope.getUpperCorner().getOrdinate(0))
            .setMaxy(envelope.getUpperCorner().getOrdinate(1))
            .setMinx(envelope.getLowerCorner().getOrdinate(0))
            .setMiny(envelope.getLowerCorner().getOrdinate(1));
  }

  public Bounds toJsonPojo() {
    // TODO: gegenereerde Bounds class niet nodig, gewoon Jackson/JAXB annotaties op deze class
    return new Bounds().crs(crs).minx(minx).miny(miny).maxx(maxx).maxy(maxy);
  }
}
