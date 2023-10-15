/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence.helper;

import java.math.BigDecimal;
import nl.b3p.tailormap.api.persistence.json.TMAttributeType;
import org.geotools.api.feature.type.AttributeType;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.ows.wms.CRSEnvelope;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

public class GeoToolsHelper {

  public static nl.b3p.tailormap.api.persistence.json.Bounds boundsFromCRSEnvelope(
      CRSEnvelope crsEnvelope) {
    return crsEnvelope == null
        ? null
        : new nl.b3p.tailormap.api.persistence.json.Bounds()
            .maxx(crsEnvelope.getMaxX())
            .maxy(crsEnvelope.getMaxY())
            .minx(crsEnvelope.getMinX())
            .miny(crsEnvelope.getMinY());
  }

  public static nl.b3p.tailormap.api.persistence.json.Bounds fromCRS(
      CoordinateReferenceSystem crs) {
    org.geotools.api.geometry.Bounds envelope = CRS.getEnvelope(crs);
    return envelope == null
        ? null
        : new nl.b3p.tailormap.api.persistence.json.Bounds()
            // ordinate choice may not always be correct...eg. with flipped axis
            .maxx(envelope.getUpperCorner().getOrdinate(0))
            .maxy(envelope.getUpperCorner().getOrdinate(1))
            .minx(envelope.getLowerCorner().getOrdinate(0))
            .miny(envelope.getLowerCorner().getOrdinate(1));
  }

  public static nl.b3p.tailormap.api.persistence.json.Bounds fromEnvelope(Envelope envelope) {
    return envelope == null
        ? null
        : new nl.b3p.tailormap.api.persistence.json.Bounds()
            .maxx(envelope.getMaxX())
            .maxy(envelope.getMaxY())
            .minx(envelope.getMinX())
            .miny(envelope.getMinY());
  }

  public static String crsToString(CoordinateReferenceSystem crs) {
    return crs == null ? null : CRS.toSRS(crs);
  }

  public static TMAttributeType toAttributeType(AttributeType gtType) {
    Class<?> binding = gtType.getBinding();
    if (binding.equals(MultiPolygon.class)) {
      return TMAttributeType.MULTIPOLYGON;
    }
    if (binding.equals(Polygon.class)) {
      return TMAttributeType.POLYGON;
    }
    if (binding.equals(MultiLineString.class)) {
      return TMAttributeType.MULTILINESTRING;
    }
    if (binding.equals(LineString.class)) {
      return TMAttributeType.LINESTRING;
    }
    if (binding.equals(MultiPoint.class)) {
      return TMAttributeType.MULTIPOINT;
    }
    if (binding.equals(Point.class)) {
      return TMAttributeType.POINT;
    }
    if (binding.equals(Geometry.class)) {
      return TMAttributeType.GEOMETRY;
    }
    if (binding.equals(GeometryCollection.class)) {
      return TMAttributeType.GEOMETRY_COLLECTION;
    }
    if (binding.equals(Boolean.class)) {
      return TMAttributeType.BOOLEAN;
    }
    if (binding.equals(Long.class)
        || binding.equals(Integer.class)
        || binding.equals(Short.class)) {
      return TMAttributeType.INTEGER;
    }
    if (binding.equals(Double.class)
        || binding.equals(Float.class)
        || binding.equals(BigDecimal.class)) {
      return TMAttributeType.DOUBLE;
    }
    if (binding.equals(String.class)) {
      return TMAttributeType.STRING;
    }
    if (binding.equals(java.sql.Timestamp.class)) {
      return TMAttributeType.TIMESTAMP;
    }
    if (binding.equals(java.sql.Date.class)) {
      return TMAttributeType.DATE;
    }
    return TMAttributeType.OBJECT;
  }
}
