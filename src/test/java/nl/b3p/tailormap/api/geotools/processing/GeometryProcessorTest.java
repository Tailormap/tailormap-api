/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.geotools.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import nl.b3p.tailormap.api.StaticTestData;

import org.geotools.geometry.jts.WKTReader2;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.Stopwatch;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;

class GeometryProcessorTest extends StaticTestData {

    @Stopwatch
    @Test
    void simplifyPoint() throws ParseException {
        final Geometry p = new WKTReader2().read(testData.getProperty("RDpointWkt"));
        assertEquals(
                testData.getProperty("RDpointWkt"),
                GeometryProcessor.processGeometry(p, true),
                "simplified geometry should match");
    }

    @Test
    void doNotSimplifyPoint() throws ParseException {
        final Geometry p = new WKTReader2().read(testData.getProperty("RDpointWkt"));
        assertEquals(
                testData.getProperty("RDpointWkt"),
                GeometryProcessor.processGeometry(p, false),
                "simplified geometry should match");
    }

    @Stopwatch
    @Test
    void simplifyPolygon() throws ParseException {
        final Geometry p = new WKTReader2().read(testData.getProperty("RDpolygonWkt"));
        final String simplified = GeometryProcessor.processGeometry(p, true);
        assertNotEquals(
                testData.getProperty("RDpolygonWkt"),
                simplified,
                "simplified geometry should not match");

        final Geometry p2 = new WKTReader2().read(simplified);
        assertTrue(p.overlaps(p2), "source polygon should overlap simplified");
        /* PMD.JUnitAssertionsShouldIncludeMessage */
        assertThat(100 * p2.intersection(p).getArea() / p.getArea()) // NOPMD
                .as("intersection area should be about 99% of original")
                .isCloseTo(99, within(1d));
    }

    @Test
    void doNotSimplifyPolygon() throws ParseException {
        final Geometry p = new WKTReader2().read(testData.getProperty("RDpolygonWkt"));
        assertEquals(
                testData.getProperty("RDpolygonWkt"),
                GeometryProcessor.processGeometry(p, false),
                "simplified geometry does not match");
    }

    @Test
    void testLinearizeCurvePolygonUnsimplified() throws ParseException {
        final Geometry c = new WKTReader2().read(testData.getProperty("curvePolygon"));
        assertEquals(
                testData.getProperty("curvePolygonLinearized"),
                GeometryProcessor.processGeometry(c, false),
                "geometry should be linearized");
    }

    @Test
    void donNotSimplifyMultiPolygon() throws ParseException {
        final Geometry p = new WKTReader2().read(testData.getProperty("multiPolygon"));
        assertEquals(
                testData.getProperty("multiPolygon"),
                GeometryProcessor.processGeometry(p, false),
                "geometry should be the same");
    }

    @Test
    void testLinearRing() throws ParseException {
        final Geometry ring = new WKTReader2().read(testData.getProperty("linearRing"));
        assertEquals(
                testData.getProperty("lineString"),
                GeometryProcessor.processGeometry(ring, false),
                "geometry does not match");
    }
}
