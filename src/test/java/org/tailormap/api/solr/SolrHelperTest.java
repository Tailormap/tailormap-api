/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.solr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.mockito.ArgumentCaptor;
import org.tailormap.api.geotools.processing.GeometryProcessor;
import org.tailormap.api.persistence.SearchIndex;
import org.tailormap.api.viewer.model.SearchResponse;

class SolrHelperTest {

  @Test
  void find_in_index_transforms_spatial_query_and_result_geometry() throws Exception {
    SolrClient solrClient = mock(SolrClient.class);
    QueryResponse queryResponse = mock(QueryResponse.class);

    String sourceGeometryWkt = "POINT (3.7651071548461914 51.742008209228516)";

    SolrDocument document = new SolrDocument();
    document.setField("id", "lgstations.108");
    document.setField("displayFields", List.of("Jan van Renesseweg"));
    document.setField("geometry", sourceGeometryWkt);

    SolrDocumentList documents = new SolrDocumentList();
    documents.add(document);
    documents.setNumFound(1);
    documents.setStart(0);

    when(queryResponse.getResults()).thenReturn(documents);
    when(solrClient.query(any(SolrQuery.class))).thenReturn(queryResponse);

    SearchIndex searchIndex = new SearchIndex()
        .setId(7L)
        .setName("Reddingposten")
        .setSourceCrs("EPSG:4326")
        .setSpatialIndexVersion(2);

    Geometry sourcePoint = GeometryProcessor.wktToGeometry(sourceGeometryWkt);
    assertNotNull(sourcePoint);

    /*
     * Known EPSG:3857 coordinates for longitude 3.7651071548461914,
     * latitude 51.742008209228516. Keep these values independent from
     * TransformationUtil so axis-order regressions are detected.
     */
    String applicationPoint = "419129.8112595891 6753610.983561384";
    Geometry applicationPointGeometry = GeometryProcessor.wktToGeometry("POINT (" + applicationPoint + ")");
    assertNotNull(applicationPointGeometry);

    double distance = 0.005;

    SearchResponse response;
    try (SolrHelper solrHelper = new SolrHelper(solrClient)) {
      response = solrHelper.findInIndex(searchIndex, "*", null, applicationPoint, distance, 0, 10, "EPSG:3857");
    }

    ArgumentCaptor<SolrQuery> queryCaptor = ArgumentCaptor.forClass(SolrQuery.class);
    verify(solrClient).query(queryCaptor.capture());

    SolrQuery sentQuery = queryCaptor.getValue();

    /*
     * The point supplied by the application must be transformed back to the
     * source CRS before it is sent to Solr.
     */
    String transformedPoint = sentQuery.get("pt");
    assertNotNull(transformedPoint);

    Geometry transformedPointGeometry = GeometryProcessor.wktToGeometry("POINT (" + transformedPoint + ")");

    assertNotNull(transformedPointGeometry);
    assertEquals(sourcePoint.getCoordinate().x, transformedPointGeometry.getCoordinate().x, 0.000001);
    assertEquals(sourcePoint.getCoordinate().y, transformedPointGeometry.getCoordinate().y, 0.000001);

    /*
     * Solr distance is already expressed in its configured distance unit and
     * must not be transformed together with the point coordinates.
     */
    assertEquals("0.005", sentQuery.get("d"));

    assertEquals(1, response.getDocuments().size());

    String geometry = response.getDocuments().getFirst().getGeometry();
    assertNotNull(geometry);

    /*
     * Geometry stored in Solr uses the source CRS. The geometry returned by
     * the API must use the application CRS.
     */
    Geometry transformedGeometry = GeometryProcessor.wktToGeometry(geometry);
    assertNotNull(transformedGeometry);

    assertEquals(applicationPointGeometry.getCoordinate().x, transformedGeometry.getCoordinate().x, 0.001);
    assertEquals(applicationPointGeometry.getCoordinate().y, transformedGeometry.getCoordinate().y, 0.001);
  }

  @Test
  void find_in_index_does_not_transform_when_source_and_application_crs_are_equal() throws Exception {
    SolrClient solrClient = mock(SolrClient.class);
    QueryResponse queryResponse = mock(QueryResponse.class);

    String geometryWkt = "POINT (3.7651071548461914 51.742008209228516)";

    SolrDocument document = new SolrDocument();
    document.setField("id", "lgstations.108");
    document.setField("displayFields", List.of("Jan van Renesseweg"));
    document.setField("geometry", geometryWkt);

    SolrDocumentList documents = new SolrDocumentList();
    documents.add(document);
    documents.setNumFound(1);
    documents.setStart(0);

    when(queryResponse.getResults()).thenReturn(documents);
    when(solrClient.query(any(SolrQuery.class))).thenReturn(queryResponse);

    SearchIndex searchIndex = new SearchIndex()
        .setId(7L)
        .setName("Reddingposten")
        .setSourceCrs("EPSG:4326")
        .setSpatialIndexVersion(2);

    String point = "3.7651071548461914 51.742008209228516";
    double distance = 0.005;

    SearchResponse response;
    try (SolrHelper solrHelper = new SolrHelper(solrClient)) {
      response = solrHelper.findInIndex(searchIndex, "*", null, point, distance, 0, 10, "EPSG:4326");
    }

    ArgumentCaptor<SolrQuery> queryCaptor = ArgumentCaptor.forClass(SolrQuery.class);
    verify(solrClient).query(queryCaptor.capture());

    SolrQuery sentQuery = queryCaptor.getValue();

    assertEquals(point, sentQuery.get("pt"));
    assertEquals("0.005", sentQuery.get("d"));

    assertEquals(1, response.getDocuments().size());
    assertEquals(geometryWkt, response.getDocuments().getFirst().getGeometry());
  }

  @Test
  void legacy_index_rejects_geographic_distance_search() throws Exception {
    SolrClient solrClient = mock(SolrClient.class);
    SearchIndex searchIndex = new SearchIndex().setId(7L).setName("Existing search index");

    try (SolrHelper solrHelper = new SolrHelper(solrClient)) {
      SolrException error = assertThrows(
          SolrException.class,
          () -> solrHelper.findInIndex(
              searchIndex, "*", null, "419121.8 6750463.6", 0.005, 0, 10, "EPSG:3857"));

      assertEquals(400, error.code());
    }

    verify(solrClient, never()).query(any(SolrQuery.class));
  }

  @Test
  void legacy_index_allows_non_spatial_search() throws Exception {
    SolrClient solrClient = mock(SolrClient.class);
    QueryResponse queryResponse = mock(QueryResponse.class);
    SolrDocumentList documents = new SolrDocumentList();
    documents.setNumFound(0);
    documents.setStart(0);

    when(queryResponse.getResults()).thenReturn(documents);
    when(solrClient.query(any(SolrQuery.class))).thenReturn(queryResponse);

    SearchIndex searchIndex = new SearchIndex().setId(7L).setName("Existing search index");

    try (SolrHelper solrHelper = new SolrHelper(solrClient)) {
      SearchResponse response =
          solrHelper.findInIndex(searchIndex, "reddingspost", null, null, null, 0, 10, "EPSG:3857");

      assertEquals(0, response.getDocuments().size());
    }

    verify(solrClient).query(any(SolrQuery.class));
  }

  @Test
  void geographic_filter_uses_wgs84_field() throws Exception {
    for (String filter : List.of(
        "{!geofilt sfield=geometry}", "{!bbox sfield=geometry}", "{!geofilt}", "  {!bbox sfield=geometry}")) {

      SolrClient solrClient = mock(SolrClient.class);
      QueryResponse queryResponse = mock(QueryResponse.class);
      SolrDocumentList documents = new SolrDocumentList();
      documents.setNumFound(0);
      when(queryResponse.getResults()).thenReturn(documents);
      when(solrClient.query(any(SolrQuery.class))).thenReturn(queryResponse);

      SearchIndex index = new SearchIndex()
          .setId(7L)
          .setName("Geographic test")
          .setSourceCrs("EPSG:4326")
          .setSpatialIndexVersion(2);

      try (SolrHelper solrHelper = new SolrHelper(solrClient)) {
        solrHelper.findInIndex(index, "*", filter, "3.765107 51.742008", 0.005, 0, 10, "EPSG:4326");
      }

      ArgumentCaptor<SolrQuery> captor = ArgumentCaptor.forClass(SolrQuery.class);
      verify(solrClient).query(captor.capture());

      String[] filters = captor.getValue().getFilterQueries();
      assertNotNull(filters);
      assertEquals(2, filters.length);
      assertEquals(true, filters[1].contains("sfield=geometryGeographic"));
      assertEquals(false, filters[1].contains("sfield=geometry}"));
      assertEquals("0.005", captor.getValue().get("d"));
    }
  }

  @Test
  void legacy_index_rejects_spatial_filter_with_leading_spaces() throws Exception {
    SolrClient solrClient = mock(SolrClient.class);
    SearchIndex index = new SearchIndex().setId(7L).setName("Legacy index").setSpatialIndexVersion(1);

    try (SolrHelper solrHelper = new SolrHelper(solrClient)) {
      SolrException error = assertThrows(
          SolrException.class,
          () -> solrHelper.findInIndex(
              index, "*", "  {!bbox sfield=geometry}", null, null, 0, 10, "EPSG:4326"));
      assertEquals(400, error.code());
    }

    verify(solrClient, never()).query(any(SolrQuery.class));
  }

  @Test
  void geographic_filter_rejects_unknown_spatial_field() throws Exception {
    SolrClient solrClient = mock(SolrClient.class);
    SearchIndex index =
        new SearchIndex().setId(7L).setName("Geographic test").setSpatialIndexVersion(2);

    try (SolrHelper solrHelper = new SolrHelper(solrClient)) {
      SolrException error = assertThrows(
          SolrException.class,
          () -> solrHelper.findInIndex(
              index,
              "*",
              "{!geofilt sfield=otherGeometry}",
              "3.765107 51.742008",
              0.005,
              0,
              10,
              "EPSG:4326"));
      assertEquals(400, error.code());
    }

    verify(solrClient, never()).query(any(SolrQuery.class));
  }

  @Test
  void find_in_index_transforms_rd_new_search_point_to_wgs84() throws Exception {
    SolrClient solrClient = mock(SolrClient.class);
    QueryResponse response = mock(QueryResponse.class);

    SolrDocumentList documents = new SolrDocumentList();
    documents.setNumFound(0);
    when(response.getResults()).thenReturn(documents);
    when(solrClient.query(any(SolrQuery.class))).thenReturn(response);

    SearchIndex index = new SearchIndex()
        .setId(7L)
        .setName("RD New index")
        .setSourceCrs("EPSG:28992")
        .setSpatialIndexVersion(2);

    try (SolrHelper helper = new SolrHelper(solrClient)) {
      helper.findInIndex(index, "*", null, "155000 463000", 0.005, 0, 10, "EPSG:28992");
    }

    ArgumentCaptor<SolrQuery> captor = ArgumentCaptor.forClass(SolrQuery.class);
    verify(solrClient).query(captor.capture());

    SolrQuery query = captor.getValue();

    Geometry point = GeometryProcessor.wktToGeometry("POINT (" + query.get("pt") + ")");

    assertNotNull(point);
    assertEquals(5.3872035084, point.getCoordinate().x, 0.00001);
    assertEquals(52.1551723012, point.getCoordinate().y, 0.00001);

    assertEquals("0.005", query.get("d"));
    assertEquals(true, List.of(query.getFilterQueries()).contains("{!geofilt sfield=geometryGeographic}"));
  }

  @Test
  void geographic_filter_rejects_local_point_and_distance() throws Exception {
    SolrClient solrClient = mock(SolrClient.class);

    SearchIndex index =
        new SearchIndex().setId(7L).setName("Geographic test").setSpatialIndexVersion(2);

    try (SolrHelper helper = new SolrHelper(solrClient)) {
      SolrException error = assertThrows(
          SolrException.class,
          () -> helper.findInIndex(
              index,
              "*",
              "{!geofilt sfield=geometry pt=3.765,51.742 d=0.005}",
              "3.765 51.742",
              0.005,
              0,
              10,
              "EPSG:4326"));

      assertEquals(400, error.code());
    }

    verify(solrClient, never()).query(any(SolrQuery.class));
  }
}
