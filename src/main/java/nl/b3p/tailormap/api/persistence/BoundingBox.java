/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import javax.persistence.Embeddable;
import nl.b3p.tailormap.api.viewer.model.Bounds;

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

  public Bounds toJsonPojo() {
    // TODO: gegenereerde Bounds class niet nodig, gewoon Jackson/JAXB annotaties op deze class
    return new Bounds().crs(crs).minx(minx).miny(miny).maxx(maxx).maxy(maxy);
  }
}
