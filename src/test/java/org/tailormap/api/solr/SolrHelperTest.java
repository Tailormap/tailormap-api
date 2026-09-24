/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.solr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.geotools.api.referencing.operation.MathTransform;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.mockito.ArgumentCaptor;
import org.tailormap.api.geotools.TransformationUtil;
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

    SearchIndex searchIndex =
        new SearchIndex().setId(7L).setName("Reddingposten").setSourceCrs("EPSG:4326");

    /*
     * Construct the application point using the same GeoTools transformation
     * mechanism used by Tailormap. This avoids hard-coded projected coordinates
     * and CRS axis-order assumptions in the test.
     */
    Geometry sourcePoint = GeometryProcessor.wktToGeometry(sourceGeometryWkt);
    assertNotNull(sourcePoint);

    MathTransform toApplication = TransformationUtil.getTransformation("EPSG:4326", "EPSG:3857");
    assertNotNull(toApplication);

    Geometry applicationPointGeometry = GeometryProcessor.transformGeometry(sourcePoint, toApplication);

    String applicationPoint =
        applicationPointGeometry.getCoordinate().x + " " + applicationPointGeometry.getCoordinate().y;

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

    SearchIndex searchIndex =
        new SearchIndex().setId(7L).setName("Reddingposten").setSourceCrs("EPSG:4326");

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
  void find_in_index_without_source_crs_preserves_existing_behavior() throws Exception {
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

    /*
     * Existing search_index records will initially have a null sourceCrs after
     * the database migration, until the index has been rebuilt.
     */
    SearchIndex searchIndex = new SearchIndex().setId(7L).setName("Existing search index");

    String point = "419121.8 6750463.6";
    double distance = 0.005;

    SearchResponse response;
    try (SolrHelper solrHelper = new SolrHelper(solrClient)) {
      response = solrHelper.findInIndex(searchIndex, "*", null, point, distance, 0, 10, "EPSG:3857");
    }

    ArgumentCaptor<SolrQuery> queryCaptor = ArgumentCaptor.forClass(SolrQuery.class);
    verify(solrClient).query(queryCaptor.capture());

    SolrQuery sentQuery = queryCaptor.getValue();

    /*
     * Without a stored source CRS Tailormap cannot safely transform either the
     * spatial query point or the returned geometry, so the previous behaviour
     * is retained.
     */
    assertEquals(point, sentQuery.get("pt"));
    assertEquals("0.005", sentQuery.get("d"));

    assertEquals(1, response.getDocuments().size());
    assertEquals(geometryWkt, response.getDocuments().getFirst().getGeometry());
  }
}
