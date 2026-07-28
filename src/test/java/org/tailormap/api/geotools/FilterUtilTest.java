/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.Or;
import org.geotools.api.filter.spatial.Intersects;
import org.geotools.api.referencing.FactoryException;
import org.geotools.filter.IsEqualsToImpl;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.tailormap.api.persistence.Application;

class FilterUtilTest {

  // Netherlands bounding box in RD New (EPSG:28992)
  private static final String RD_NL_POLYGON =
      "POLYGON((10000 306000, 280000 306000, 280000 640000, 10000 640000, 10000 306000))";

  private static SimpleFeatureSource RDFeatureSource;

  private static final Application RD_NL_APPLICATION = mock(Application.class, invocation -> {
    if ("getCrs".equals(invocation.getMethod().getName())) {
      return "EPSG:28992";
    }
    return RETURNS_DEFAULTS.answer(invocation);
  });

  @BeforeAll
  static void setupMocks() throws FactoryException {
    RDFeatureSource = mockFeatureSource("EPSG:28992");
  }

  private static SimpleFeatureSource mockFeatureSource(String crs) throws FactoryException {
    SimpleFeatureSource featureSource = mock(SimpleFeatureSource.class);
    SimpleFeatureType schema = mock(SimpleFeatureType.class);
    when(featureSource.getSchema()).thenReturn(schema);
    when(schema.getCoordinateReferenceSystem()).thenReturn(CRS.decode(crs));
    return featureSource;
  }

  @Test
  void non_spatial_filter_returns_parsed_filter() throws CQLException, FactoryException {
    Filter filter = FilterUtil.parseFilter("name = 'test'", RD_NL_APPLICATION, RDFeatureSource);
    assertNotNull(filter);
    assertInstanceOf(IsEqualsToImpl.class, filter);
    assertEquals("[ name = test ]", filter.toString());
  }

  @Test
  void spatial_filter_same_crs_everywhere_no_transform() throws CQLException, FactoryException {
    // filter SRID = app CRS = data source CRS → no transformation should occur
    String cql = "INTERSECTS(geometry, SRID=28992;" + RD_NL_POLYGON + ")";
    Filter filter = FilterUtil.parseFilter(cql, RD_NL_APPLICATION, RDFeatureSource);

    assertNotNull(filter);
    assertInstanceOf(Intersects.class, filter);
    // geometry stays in RD New, so X coordinate is >> 10
    Geometry geom = (Geometry) ((Intersects) filter).getExpression2().evaluate(null);
    assertNotNull(geom);
    assertTrue(geom.getCentroid().getX() > 10000, "X should be in RD New range (not WGS84 longitude)");
  }

  @Test
  void spatial_filter_app_crs_matches_filter_srid_data_source_crs_differs_transform_applied()
      throws CQLException, FactoryException {
    // filter SRID = app CRS = EPSG:28992, data source = EPSG:3857 → geometry transformed to WGS84
    SimpleFeatureSource featureSource = mockFeatureSource("EPSG:3857");

    String cql = "INTERSECTS(geometry, SRID=28992;" + RD_NL_POLYGON + ")";
    Filter filter = FilterUtil.parseFilter(cql, RD_NL_APPLICATION, featureSource);

    assertNotNull(filter);
    assertInstanceOf(Intersects.class, filter);

    Geometry transformedGeom =
        (Geometry) ((Intersects) filter).getExpression2().evaluate(null);
    assertNotNull(transformedGeom);
    // Netherlands in EPSG:3857:
    // Minimum Bounds (South-West Corner): X: 388,275.01, Y: 6,594,835.97
    // Maximum Bounds (North-East Corner): X: 775,208.11, Y: 7,025,835.01
    double x = transformedGeom.getCentroid().getX();
    double y = transformedGeom.getCentroid().getY();
    assertThat(
        "Longitude should be in Netherlands EPSG:3857 range, was: " + x,
        x,
        is(both(greaterThan(388275d)).and(lessThan(775208d))));
    assertThat(
        "Latitude should be in Netherlands EPSG:3857 range, was: " + y,
        y,
        is(both(greaterThan(6594835d)).and(lessThan(7025835d))));
  }

  @Test
  void spatial_filter_srid_does_not_match_app_crs_throws() {
    // filter SRID = EPSG:3857, app CRS = EPSG:28992 → must throw
    String cql = "INTERSECTS(geometry, SRID=3857;POLYGON((3.3 51.5, 7.2 51.5, 7.2 53.5, 3.3 53.5, 3.3 51.5)))";
    assertThrows(
        UnsupportedOperationException.class,
        () -> FilterUtil.parseFilter(cql, RD_NL_APPLICATION, RDFeatureSource));
  }

  @Test
  void multiple_different_srids_in_filter_throws() {
    String cql =
        "INTERSECTS(geometry, SRID=28992;POINT(100000 400000)) OR INTERSECTS(geometry, SRID=3857;POINT(624339 6822408))";
    assertThrows(
        UnsupportedOperationException.class,
        () -> FilterUtil.parseFilter(cql, RD_NL_APPLICATION, RDFeatureSource));
  }

  @Test
  void multiple_same_srids_in_filter_succeeds() throws CQLException, FactoryException {
    // Two INTERSECTS with identical SRIDs → no exception
    String cql =
        "INTERSECTS(geometry, SRID=28992;POINT(100000 400000)) OR INTERSECTS(geometry, SRID=28992;POINT(200000 500000))";

    Filter filter = FilterUtil.parseFilter(cql, RD_NL_APPLICATION, RDFeatureSource);
    assertNotNull(filter);
    assertInstanceOf(Or.class, filter, "Filter should be an Or filter");
  }

  @Test
  void invalid_cql_throws_cql_exception() {
    assertThrows(
        CQLException.class,
        () -> FilterUtil.parseFilter("NOT VALID CQL !!!", RD_NL_APPLICATION, RDFeatureSource));
  }
}
