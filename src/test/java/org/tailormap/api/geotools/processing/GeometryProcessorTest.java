/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.processing;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.geometry.jts.WKTReader2;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.Stopwatch;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.tailormap.api.StaticTestData;

class GeometryProcessorTest extends StaticTestData {

  @Stopwatch
  @Test
  void simplify_point() throws ParseException {
    final Geometry p = new WKTReader2().read(testData.getProperty("RDpointWkt"));
    assertEquals(
        testData.getProperty("RDpointWkt"),
        GeometryProcessor.processGeometry(p, true, true, null),
        "simplified geometry should match");
  }

  @Test
  void do_not_simplify_point() throws ParseException {
    final Geometry p = new WKTReader2().read(testData.getProperty("RDpointWkt"));
    assertEquals(
        testData.getProperty("RDpointWkt"),
        GeometryProcessor.processGeometry(p, false, true, null),
        "simplified geometry should match");
  }

  @Stopwatch
  @Test
  void reproject_point() throws ParseException, FactoryException {
    final Geometry p = new WKTReader2().read(testData.getProperty("RDpointWkt"));
    MathTransform transform = CRS.findMathTransform(CRS.decode("EPSG:28992"), CRS.decode("EPSG:4326"), true);

    final Geometry reprojected = new WKTReader2().read(GeometryProcessor.processGeometry(p, true, true, transform));
    final Geometry expected = new WKTReader2().read(testData.getProperty("WGS84pointWkt"));
    assertEquals(
        expected.getCoordinate().getX(),
        reprojected.getCoordinate().getX(),
        .1,
        "X-coord of simplified, reprojected geometry should match");
    assertEquals(
        expected.getCoordinate().getY(),
        reprojected.getCoordinate().getY(),
        .1,
        "Y-coord of simplified, reprojected geometry should match");
  }

  @Stopwatch
  @Test
  void transform_point() throws ParseException, FactoryException {
    final Geometry p = new WKTReader2().read(testData.getProperty("RDpointWkt"));
    MathTransform transform = CRS.findMathTransform(CRS.decode("EPSG:28992"), CRS.decode("EPSG:4326"), true);

    final Geometry reprojected = GeometryProcessor.transformGeometry(p, transform);
    final Geometry expected = new WKTReader2().read(testData.getProperty("WGS84pointWkt"));
    assertEquals(
        expected.getCoordinate().getX(),
        reprojected.getCoordinate().getX(),
        .1,
        "X-coord of simplified, reprojected geometry should match");
    assertEquals(
        expected.getCoordinate().getY(),
        reprojected.getCoordinate().getY(),
        .1,
        "Y-coord of simplified, reprojected geometry should match");
  }

  @Stopwatch
  @Test
  void simplify_polygon() throws ParseException {
    final Geometry p = new WKTReader2().read(testData.getProperty("RDpolygonWkt"));
    final String simplified = GeometryProcessor.processGeometry(p, true, true, null);
    assertNotEquals(testData.getProperty("RDpolygonWkt"), simplified, "simplified geometry should not match");

    final Geometry p2 = new WKTReader2().read(simplified);
    assertTrue(p.overlaps(p2), "source polygon should overlap simplified");
    //    /* PMD.JUnitAssertionsShouldIncludeMessage */
    //    assertThat(100 * p2.intersection(p).getArea() / p.getArea())
    //        .as("intersection area should be about 99% of original")
    //        .isCloseTo(99, within(1d));
    assertThat(
        "intersection area should be about 99% of original",
        100 * p2.intersection(p).getArea() / p.getArea(), closeTo(99, 1d));

    //        .isCloseTo(99, within(1d));
  }

  @Test
  void do_not_simplify_polygon() throws ParseException {
    final Geometry p = new WKTReader2().read(testData.getProperty("RDpolygonWkt"));
    assertEquals(
        testData.getProperty("RDpolygonWkt"),
        GeometryProcessor.processGeometry(p, false, true, null),
        "simplified geometry does not match");
  }

  @Test
  void linearize_curve_polygon_unsimplified() throws ParseException {
    final Geometry c = new WKTReader2().read(testData.getProperty("curvePolygon"));
    assertEquals(
        testData.getProperty("curvePolygonLinearized"),
        GeometryProcessor.processGeometry(c, false, true, null),
        "geometry should be linearized");
  }

  @Test
  void do_not_simplify_multi_polygon() throws ParseException {
    final Geometry p = new WKTReader2().read(testData.getProperty("multiPolygon"));
    assertEquals(
        testData.getProperty("multiPolygon"),
        GeometryProcessor.processGeometry(p, false, true, null),
        "geometry should be the same");
  }

  @Test
  void process_linear_ring() throws ParseException {
    final Geometry ring = new WKTReader2().read(testData.getProperty("linearRing"));
    assertEquals(
        testData.getProperty("lineString"),
        GeometryProcessor.processGeometry(ring, false, true, null),
        "geometry does not match");
  }

  @Test
  void validate_json_output() throws ParseException {
    Geometry p = new WKTReader2().read(testData.getProperty("RDpointWkt"));
    assertEquals(
        "{\"type\":\"Point\",\"coordinates\":[141247,458118]}",
        GeometryProcessor.geometryToJson(p),
        "json output should match");

    // this is ignored by the json output
    p.setSRID(28992);
    assertEquals(
        "{\"type\":\"Point\",\"coordinates\":[141247,458118]}",
        GeometryProcessor.geometryToJson(p),
        "json output should match");

    p = null;
    //noinspection ConstantValue
    assertEquals("null", GeometryProcessor.geometryToJson(p), "json output should match");
  }

  @Test
  void wkt_input_output() {
    assertEquals(
        testData.getProperty("RDpointWkt"),
        GeometryProcessor.geometryToWKT(GeometryProcessor.wktToGeometry(testData.getProperty("RDpointWkt"))));
  }
}
