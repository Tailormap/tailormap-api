package org.tailormap.api.solr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.util.NamedList;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.Name;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.mockito.ArgumentCaptor;
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.geotools.processing.GeometryProcessor;
import org.tailormap.api.persistence.SearchIndex;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.repository.SearchIndexRepository;

class SolrHelperGeographicIndexingTest {

  @Test
  void indexes_web_mercator_geometry_as_wgs84() throws Exception {
    checkIndexing(
        "EPSG:3857", "POINT (419129.8112595891 6753610.983561384)", 3.7651071548461914, 51.742008209228516);
  }

  @Test
  void indexes_rd_new_geometry_as_wgs84() throws Exception {
    checkIndexing("EPSG:28992", "POINT (155000 463000)", 5.3872035084, 52.1551723012);
  }

  private void checkIndexing(String sourceCrs, String sourceWkt, double expectedLongitude, double expectedLatitude)
      throws Exception {

    SolrClient solr = mock(SolrClient.class);
    SearchIndexRepository repository = mock(SearchIndexRepository.class);
    FeatureSourceFactoryHelper factory = mock(FeatureSourceFactoryHelper.class);

    TMFeatureType featureType = new TMFeatureType()
        .setName("CRS test features")
        .setPrimaryKeyAttribute("id")
        .setDefaultGeometryAttribute("geom");

    SearchIndex index = new SearchIndex()
        .setId(7L)
        .setName("CRS test index")
        .setSearchFieldsUsed(List.of("name"))
        .setSearchDisplayFieldsUsed(List.of("name"))
        .setSpatialIndexVersion(1);

    when(repository.save(any(SearchIndex.class))).thenAnswer(invocation -> invocation.getArgument(0));

    SimpleFeatureSource source = mock(SimpleFeatureSource.class);
    SimpleFeatureType schema = mock(SimpleFeatureType.class);
    SimpleFeatureCollection collection = mock(SimpleFeatureCollection.class);
    SimpleFeatureIterator iterator = mock(SimpleFeatureIterator.class);
    SimpleFeature feature = mock(SimpleFeature.class);
    DataStore dataStore = mock(DataStore.class);

    when(factory.openGeoToolsFeatureSource(featureType)).thenReturn(source);
    when(source.getName()).thenReturn(mock(Name.class));
    when(source.getSchema()).thenReturn(schema);
    when(schema.getCoordinateReferenceSystem()).thenReturn(CRS.decode(sourceCrs, true));
    when(source.getFeatures(any(Query.class))).thenReturn(collection);
    when(source.getDataStore()).thenReturn(dataStore);

    when(collection.size()).thenReturn(1);
    when(collection.features()).thenReturn(iterator);
    when(iterator.hasNext()).thenReturn(true, false);
    when(iterator.next()).thenReturn(feature);

    Geometry original = GeometryProcessor.wktToGeometry(sourceWkt);
    assertNotNull(original);

    when(feature.getID()).thenReturn("test.1");
    when(feature.getAttribute("geom")).thenReturn(original);
    when(feature.getAttribute("name")).thenReturn("Test station");
    when(feature.getAttribute("id")).thenReturn(1L);

    NamedList<Object> field = new NamedList<>();
    field.add("name", "existing");

    NamedList<Object> fieldType = new NamedList<>();
    fieldType.add("name", "existing");

    NamedList<Object> schemaResponse = new NamedList<>();
    schemaResponse.add("field", field);
    schemaResponse.add("fieldType", fieldType);

    when(solr.request(any(SolrRequest.class), nullable(String.class))).thenReturn(schemaResponse);

    QueryResponse queryResponse = mock(QueryResponse.class);
    SolrDocumentList documents = new SolrDocumentList();
    documents.setNumFound(0);

    when(queryResponse.getResults()).thenReturn(documents);
    when(solr.query(any(SolrQuery.class))).thenReturn(queryResponse);

    UpdateResponse commitResponse = mock(UpdateResponse.class);
    when(commitResponse.getStatus()).thenReturn(0);
    when(solr.commit()).thenReturn(commitResponse);

    try (SolrHelper helper = new SolrHelper(solr)) {
      helper.addFeatureTypeIndex(index, featureType, factory, repository);
    }

    ArgumentCaptor<Collection> documentsCaptor = ArgumentCaptor.forClass(Collection.class);

    verify(solr).addBeans(documentsCaptor.capture(), anyInt());

    Collection<?> indexedDocuments = documentsCaptor.getValue();

    assertEquals(1, indexedDocuments.size());

    FeatureIndexingDocument indexed =
        (FeatureIndexingDocument) indexedDocuments.iterator().next();

    Geometry storedOriginal = GeometryProcessor.wktToGeometry(indexed.getGeometry());
    Geometry storedGeographic = GeometryProcessor.wktToGeometry(indexed.getGeometryGeographic());

    assertNotNull(storedOriginal);
    assertNotNull(storedGeographic);

    // Original geometry must retain the coordinates of its source CRS.
    assertEquals(original.getCoordinate().x, storedOriginal.getCoordinate().x, 0.001);
    assertEquals(original.getCoordinate().y, storedOriginal.getCoordinate().y, 0.001);

    // Geographic geometry must be longitude/latitude in WGS84.
    assertEquals(expectedLongitude, storedGeographic.getCoordinate().x, 0.00001);
    assertEquals(expectedLatitude, storedGeographic.getCoordinate().y, 0.00001);

    assertEquals(2, index.getSpatialIndexVersion());
    assertEquals(SearchIndex.Status.INDEXED, index.getStatus());

    verify(iterator).close();
    verify(dataStore).dispose();

    assertTrue(indexed.getGeometryGeographic().startsWith("POINT"));
  }
}
