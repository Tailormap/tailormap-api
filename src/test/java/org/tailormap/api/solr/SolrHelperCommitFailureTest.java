package org.tailormap.api.solr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.Name;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.referencing.CRS;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DataAccessResourceFailureException;
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.persistence.SearchIndex;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.repository.SearchIndexRepository;

class SolrHelperCommitFailureTest {

  @Test
  void failed_final_commit_never_marks_geographic_index_ready() throws Exception {

    SolrClient solr = mock(SolrClient.class);
    SearchIndexRepository repository = mock(SearchIndexRepository.class);
    FeatureSourceFactoryHelper factory = mock(FeatureSourceFactoryHelper.class);

    TMFeatureType featureType = new TMFeatureType()
        .setName("Test features")
        .setPrimaryKeyAttribute("id")
        .setDefaultGeometryAttribute("geom");

    SearchIndex index = new SearchIndex()
        .setId(7L)
        .setName("Test index")
        .setSearchFieldsUsed(List.of("name"))
        .setSearchDisplayFieldsUsed(List.of("name"))
        .setSpatialIndexVersion(2);

    when(repository.save(any(SearchIndex.class))).thenAnswer(invocation -> invocation.getArgument(0));

    SimpleFeatureSource source = mock(SimpleFeatureSource.class);
    SimpleFeatureType schema = mock(SimpleFeatureType.class);
    SimpleFeatureCollection collection = mock(SimpleFeatureCollection.class);
    SimpleFeatureIterator iterator = mock(SimpleFeatureIterator.class);
    DataStore dataStore = mock(DataStore.class);
    Name name = mock(Name.class);

    when(factory.openGeoToolsFeatureSource(featureType)).thenReturn(source);
    when(source.getName()).thenReturn(name);
    when(source.getSchema()).thenReturn(schema);
    when(schema.getCoordinateReferenceSystem()).thenReturn(CRS.decode("EPSG:4326", true));
    when(source.getFeatures(any(Query.class))).thenReturn(collection);
    when(collection.size()).thenReturn(0);
    when(collection.features()).thenReturn(iterator);
    when(iterator.hasNext()).thenReturn(false);
    when(source.getDataStore()).thenReturn(dataStore);

    // Simulate existing Solr schema fields and field types.
    // No real Solr request is sent.
    NamedList<Object> field = new NamedList<>();
    field.add("name", "existing");

    NamedList<Object> fieldType = new NamedList<>();
    fieldType.add("name", "existing");

    NamedList<Object> schemaResponse = new NamedList<>();
    schemaResponse.add("field", field);
    schemaResponse.add("fieldType", fieldType);

    when(solr.request(any(SolrRequest.class), nullable(String.class))).thenReturn(schemaResponse);

    // No existing documents to delete; only the final commit is reached.
    QueryResponse queryResponse = mock(QueryResponse.class);
    SolrDocumentList documents = new SolrDocumentList();
    documents.setNumFound(0);

    when(queryResponse.getResults()).thenReturn(documents);
    when(solr.query(any(SolrQuery.class))).thenReturn(queryResponse);

    when(solr.commit()).thenThrow(new IOException("Simulated final Solr commit failure"));

    try (SolrHelper helper = new SolrHelper(solr)) {
      IOException error = assertThrows(
          IOException.class, () -> helper.addFeatureTypeIndex(index, featureType, factory, repository));

      assertEquals("Simulated final Solr commit failure", error.getMessage());
    }

    assertEquals(1, index.getSpatialIndexVersion());

    verify(repository, atLeastOnce()).save(any(SearchIndex.class));
    verify(repository, never())
        .save(ArgumentMatchers.argThat(saved -> Integer.valueOf(2).equals(saved.getSpatialIndexVersion())));

    verify(solr).commit();
    verify(iterator).close();
    verify(dataStore).dispose();
  }

  @Test
  void schema_connection_failure_prevents_indexing() throws Exception {
    SolrClient solr = mock(SolrClient.class);
    SearchIndexRepository repository = mock(SearchIndexRepository.class);
    FeatureSourceFactoryHelper factory = mock(FeatureSourceFactoryHelper.class);

    SearchIndex index =
        new SearchIndex().setId(7L).setName("Schema failure test").setSpatialIndexVersion(1);

    TMFeatureType featureType = new TMFeatureType().setName("Test features");

    when(solr.request(any(SolrRequest.class), nullable(String.class)))
        .thenThrow(new IOException("Simulated Solr schema failure"));

    try (SolrHelper helper = new SolrHelper(solr)) {
      IOException error = assertThrows(
          IOException.class, () -> helper.addFeatureTypeIndex(index, featureType, factory, repository));

      assertEquals("Simulated Solr schema failure", error.getMessage());
    }

    assertEquals(1, index.getSpatialIndexVersion());

    verify(repository, never()).save(any(SearchIndex.class));
    verify(factory, never()).openGeoToolsFeatureSource(any(TMFeatureType.class));
    verify(solr, never()).deleteByQuery(any(String.class));
  }

  @Test
  void failed_database_invalidation_does_not_clear_solr() throws Exception {
    SolrClient solr = mock(SolrClient.class);
    SearchIndexRepository repository = mock(SearchIndexRepository.class);
    FeatureSourceFactoryHelper factory = mock(FeatureSourceFactoryHelper.class);

    TMFeatureType featureType = new TMFeatureType()
        .setName("Database failure test")
        .setPrimaryKeyAttribute("id")
        .setDefaultGeometryAttribute("geom");

    SearchIndex index = new SearchIndex()
        .setId(7L)
        .setName("Database failure index")
        .setSearchFieldsUsed(List.of("name"))
        .setSearchDisplayFieldsUsed(List.of("name"))
        .setSpatialIndexVersion(2);

    AtomicInteger saves = new AtomicInteger();

    when(repository.save(any(SearchIndex.class))).thenAnswer(invocation -> {
      if (saves.incrementAndGet() == 2) {
        throw new DataAccessResourceFailureException("Simulated database invalidation failure");
      }
      return invocation.getArgument(0);
    });

    SimpleFeatureSource source = mock(SimpleFeatureSource.class);
    SimpleFeatureType schema = mock(SimpleFeatureType.class);
    SimpleFeatureCollection collection = mock(SimpleFeatureCollection.class);
    SimpleFeatureIterator iterator = mock(SimpleFeatureIterator.class);
    DataStore dataStore = mock(DataStore.class);

    when(factory.openGeoToolsFeatureSource(featureType)).thenReturn(source);
    when(source.getName()).thenReturn(mock(Name.class));
    when(source.getSchema()).thenReturn(schema);
    when(schema.getCoordinateReferenceSystem()).thenReturn(CRS.decode("EPSG:4326", true));
    when(source.getFeatures(any(Query.class))).thenReturn(collection);
    when(collection.size()).thenReturn(0);
    when(collection.features()).thenReturn(iterator);
    when(iterator.hasNext()).thenReturn(false);
    when(source.getDataStore()).thenReturn(dataStore);

    NamedList<Object> field = new NamedList<>();
    field.add("name", "existing");

    NamedList<Object> fieldType = new NamedList<>();
    fieldType.add("name", "existing");

    NamedList<Object> schemaResponse = new NamedList<>();
    schemaResponse.add("field", field);
    schemaResponse.add("fieldType", fieldType);

    when(solr.request(any(SolrRequest.class), nullable(String.class))).thenReturn(schemaResponse);

    try (SolrHelper helper = new SolrHelper(solr)) {
      DataAccessResourceFailureException error = assertThrows(
          DataAccessResourceFailureException.class,
          () -> helper.addFeatureTypeIndex(index, featureType, factory, repository));

      assertEquals("Simulated database invalidation failure", error.getMessage());
    }

    assertEquals(2, saves.get());

    verify(solr, never()).deleteByQuery(any(String.class));
    verify(solr, never()).addBeans(ArgumentMatchers.<Collection<?>>any(), ArgumentMatchers.anyInt());
    verify(solr, never()).commit();

    verify(iterator).close();
    verify(dataStore).dispose();
  }

  @Test
  void failed_final_database_save_does_not_complete_indexing() throws Exception {
    SolrClient solr = mock(SolrClient.class);
    SearchIndexRepository repository = mock(SearchIndexRepository.class);
    FeatureSourceFactoryHelper factory = mock(FeatureSourceFactoryHelper.class);

    TMFeatureType featureType = new TMFeatureType()
        .setName("Final database failure test")
        .setPrimaryKeyAttribute("id")
        .setDefaultGeometryAttribute("geom");

    SearchIndex index = new SearchIndex()
        .setId(7L)
        .setName("Final database failure index")
        .setSearchFieldsUsed(List.of("name"))
        .setSearchDisplayFieldsUsed(List.of("name"))
        .setSpatialIndexVersion(2);

    AtomicInteger saves = new AtomicInteger();

    AtomicInteger persistedVersion = new AtomicInteger(2);

    when(repository.save(any(SearchIndex.class))).thenAnswer(invocation -> {
      SearchIndex candidate = invocation.getArgument(0);
      int saveNumber = saves.incrementAndGet();

      if (saveNumber == 3) {
        throw new DataAccessResourceFailureException("Simulated final database save failure");
      }

      persistedVersion.set(candidate.getSpatialIndexVersion());
      return candidate;
    });

    SimpleFeatureSource source = mock(SimpleFeatureSource.class);
    SimpleFeatureType schema = mock(SimpleFeatureType.class);
    SimpleFeatureCollection collection = mock(SimpleFeatureCollection.class);
    SimpleFeatureIterator iterator = mock(SimpleFeatureIterator.class);
    DataStore dataStore = mock(DataStore.class);

    when(factory.openGeoToolsFeatureSource(featureType)).thenReturn(source);
    when(source.getName()).thenReturn(mock(Name.class));
    when(source.getSchema()).thenReturn(schema);
    when(schema.getCoordinateReferenceSystem()).thenReturn(CRS.decode("EPSG:4326", true));
    when(source.getFeatures(any(Query.class))).thenReturn(collection);
    when(collection.size()).thenReturn(0);
    when(collection.features()).thenReturn(iterator);
    when(iterator.hasNext()).thenReturn(false);
    when(source.getDataStore()).thenReturn(dataStore);

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
      DataAccessResourceFailureException error = assertThrows(
          DataAccessResourceFailureException.class,
          () -> helper.addFeatureTypeIndex(index, featureType, factory, repository));

      assertEquals("Simulated final database save failure", error.getMessage());
    }

    assertEquals(3, saves.get());
    assertEquals(1, persistedVersion.get());

    verify(solr).commit();
    verify(iterator).close();
    verify(dataStore).dispose();
  }

  @Test
  void failed_delete_commit_prevents_index_rebuild() throws Exception {
    SolrClient solr = mock(SolrClient.class);
    SearchIndexRepository repository = mock(SearchIndexRepository.class);
    FeatureSourceFactoryHelper factory = mock(FeatureSourceFactoryHelper.class);

    TMFeatureType featureType = new TMFeatureType()
        .setName("Delete commit failure test")
        .setPrimaryKeyAttribute("id")
        .setDefaultGeometryAttribute("geom");

    SearchIndex index = new SearchIndex()
        .setId(7L)
        .setName("Delete commit failure index")
        .setSearchFieldsUsed(List.of("name"))
        .setSearchDisplayFieldsUsed(List.of("name"))
        .setSpatialIndexVersion(2);

    when(repository.save(any(SearchIndex.class))).thenAnswer(invocation -> invocation.getArgument(0));

    SimpleFeatureSource source = mock(SimpleFeatureSource.class);
    SimpleFeatureType schema = mock(SimpleFeatureType.class);
    SimpleFeatureCollection collection = mock(SimpleFeatureCollection.class);
    SimpleFeatureIterator iterator = mock(SimpleFeatureIterator.class);
    DataStore dataStore = mock(DataStore.class);

    when(factory.openGeoToolsFeatureSource(featureType)).thenReturn(source);
    when(source.getName()).thenReturn(mock(Name.class));
    when(source.getSchema()).thenReturn(schema);
    when(schema.getCoordinateReferenceSystem()).thenReturn(CRS.decode("EPSG:4326", true));
    when(source.getFeatures(any(Query.class))).thenReturn(collection);
    when(collection.size()).thenReturn(0);
    when(collection.features()).thenReturn(iterator);
    when(iterator.hasNext()).thenReturn(false);
    when(source.getDataStore()).thenReturn(dataStore);

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
    documents.setNumFound(1);
    when(queryResponse.getResults()).thenReturn(documents);
    when(solr.query(any(SolrQuery.class))).thenReturn(queryResponse);

    UpdateResponse deleteResponse = mock(UpdateResponse.class);
    when(deleteResponse.getStatus()).thenReturn(0);

    when(solr.deleteByQuery(any(String.class))).thenReturn(deleteResponse);

    UpdateResponse commitResponse = mock(UpdateResponse.class);
    when(commitResponse.getStatus()).thenReturn(500);
    when(solr.commit()).thenReturn(commitResponse);

    try (SolrHelper helper = new SolrHelper(solr)) {
      IOException error = assertThrows(
          IOException.class, () -> helper.addFeatureTypeIndex(index, featureType, factory, repository));

      assertTrue(error.getMessage().contains("commit failed with status 500"));
    }

    assertEquals(1, index.getSpatialIndexVersion());

    verify(solr).deleteByQuery("searchLayer:7");
    verify(solr).commit();
    verify(solr, never()).addBeans(ArgumentMatchers.<Collection<?>>any(), ArgumentMatchers.anyInt());

    verify(iterator).close();
    verify(dataStore).dispose();
  }
}
