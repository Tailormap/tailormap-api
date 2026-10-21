/*
 * Copyright (C) 2026 The Tracking Solution
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.solr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.solr.client.solrj.RemoteSolrException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.Utils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.persistence.SearchIndex;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.repository.SearchIndexRepository;

class SolrHelperSchemaRaceTest {
  private static final String[] TARGETS = {
    "fieldtypes/tm_geometry_rpt",
    "fieldtypes/tm_geometry_geographic",
    "fields/searchLayer",
    "fields/geometry",
    "fields/geometryGeographic",
    "fields/searchFields",
    "fields/displayFields"
  };

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
  void concurrent_identical_creation_allows_indexing_to_continue(int target) throws Exception {
    exercise(TARGETS[target], "matching");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
  void concurrent_incompatible_definition_is_rejected(int target) throws Exception {
    exercise(TARGETS[target], "mismatch");
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "lookup-io", "bad-request", "server-error", "creation-io"})
  void other_schema_failures_are_not_hidden(String outcome) throws Exception {
    exercise(TARGETS[1], outcome);
  }

  private void exercise(String target, String outcome) throws Exception {
    SolrClient solr = mock(SolrClient.class);
    FeatureSourceFactoryHelper factory = mock(FeatureSourceFactoryHelper.class);
    SearchIndexRepository repository = mock(SearchIndexRepository.class);
    when(repository.save(any(SearchIndex.class))).thenAnswer(invocation -> invocation.getArgument(0));
    AtomicInteger lookups = new AtomicInteger();
    AtomicInteger creations = new AtomicInteger();
    AtomicReference<Map<String, Object>> definition = new AtomicReference<>();

    RemoteSolrException duplicate = new RemoteSolrException("test", 400, "Field already exists.", null);
    RemoteSolrException badRequest = new RemoteSolrException("test", 400, "Invalid schema definition", null);
    RemoteSolrException serverError = new RemoteSolrException("test", 500, "Field already exists.", null);
    IOException creationIo = new IOException("Schema creation connection failure");
    IOException lookupIo = new IOException("Schema lookup connection failure");
    IOException reachedFeatures = new IOException("Reached feature source after schema initialization");

    when(solr.request(any(SolrRequest.class), nullable(String.class))).thenAnswer(invocation -> {
      SolrRequest<?> request = invocation.getArgument(0);
      if (request instanceof SchemaRequest.Update update) {
        creations.incrementAndGet();
        definition.set(readDefinition(update));
        switch (outcome) {
          case "bad-request" -> throw badRequest;
          case "server-error" -> throw serverError;
          case "creation-io" -> throw creationIo;
          default -> throw duplicate;
        }
      }
      if (request.getPath().equals("/schema/" + target)) {
        if (lookups.incrementAndGet() == 1 || outcome.equals("missing")) {
          throw new RemoteSolrException("test", 404, "Schema object not found", null);
        }
        if (outcome.equals("lookup-io")) {
          throw lookupIo;
        }
        Map<String, Object> actual = new HashMap<>(definition.get());
        if (outcome.equals("mismatch")) {
          actual.put(target.startsWith("fieldtypes/") ? "class" : "type", "incompatible");
        }
        return schemaResponse(actual);
      }
      return schemaResponse(Map.of("name", "existing"));
    });
    UpdateResponse committed = mock(UpdateResponse.class);
    when(committed.getStatus()).thenReturn(0);
    when(solr.commit()).thenReturn(committed);
    when(factory.openGeoToolsFeatureSource(any(TMFeatureType.class))).thenThrow(reachedFeatures);

    SearchIndex index = new SearchIndex()
        .setId(7L)
        .setName("Schema race test")
        .setSearchFieldsUsed(List.of("name"))
        .setSearchDisplayFieldsUsed(List.of("name"));
    TMFeatureType featureType = new TMFeatureType()
        .setName("Test features")
        .setPrimaryKeyAttribute("id")
        .setDefaultGeometryAttribute("geom");

    try (SolrHelper helper = new SolrHelper(solr)) {
      Exception error = assertThrows(
          Exception.class, () -> helper.addFeatureTypeIndex(index, featureType, factory, repository));
      Exception expected =
          switch (outcome) {
            case "matching" -> reachedFeatures;
            case "bad-request" -> badRequest;
            case "server-error" -> serverError;
            case "creation-io" -> creationIo;
            default -> duplicate;
          };
      assertSame(expected, error);
      if (outcome.equals("missing") || outcome.equals("lookup-io")) {
        assertEquals(1, error.getSuppressed().length);
      }
    }
    assertEquals(1, creations.get());
    assertEquals(List.of("bad-request", "server-error", "creation-io").contains(outcome) ? 1 : 2, lookups.get());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> readDefinition(SchemaRequest.Update request) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    request.getContentWriter("application/json").write(bytes);
    Map<String, Object> commands = (Map<String, Object>) Utils.fromJSON(bytes.toByteArray());
    return (Map<String, Object>) commands.values().iterator().next();
  }

  private static NamedList<Object> schemaResponse(Map<String, Object> attributes) {
    NamedList<Object> response = new NamedList<>();
    response.add("field", new NamedList<>(attributes));
    response.add("fieldType", new NamedList<>(attributes));
    return response;
  }
}
