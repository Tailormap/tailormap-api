/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.solr;

import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nl.b3p.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import nl.b3p.tailormap.api.geotools.processing.GeometryProcessor;
import nl.b3p.tailormap.api.persistence.SearchIndex;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.util.Constants;
import nl.b3p.tailormap.api.viewer.model.SearchDocument;
import nl.b3p.tailormap.api.viewer.model.SearchResponse;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.client.solrj.response.schema.SchemaResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Solr utility/wrapper class. This class provides methods to add or update a full-text feature type
 * index for a layer, find in the index for a layer, and clear the index for a layer. It also
 * provides a method to close the Solr client as well as automatically closing the client when used
 * in a try-with-resources.
 */
public class SolrHelper implements AutoCloseable, Constants {
  private final SolrClient solrClient;

  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static final int SOLR_BATCH_SIZE = 1000;
  // milliseconds
  private static final int SOLR_TIMEOUT = 7000;

  /**
   * Constructor
   *
   * @param solrClient the Solr client, this will be closed when this class is closed
   */
  public SolrHelper(@NotNull SolrClient solrClient) {
    this.solrClient = solrClient;
  }

  /**
   * Add or update a feature type index for a layer.
   *
   * @param searchIndex the search index config
   * @param tmFeatureType the feature type
   * @throws UnsupportedOperationException if the operation is not supported, possibly because not
   *     search field shave been defined
   * @throws IOException if an I/O error occurs
   * @throws SolrServerException if a Solr error occurs
   */
  @SuppressWarnings("FromTemporalAccessor")
  public void addFeatureTypeIndex(
      @NotNull SearchIndex searchIndex,
      @NotNull TMFeatureType tmFeatureType,
      @NotNull FeatureSourceFactoryHelper featureSourceFactoryHelper)
      throws UnsupportedOperationException, IOException, SolrServerException {

    createSchemaIfNotExists();

    final Instant start = Instant.now();

    if (null == tmFeatureType.getSettings().getSearchFields()) {
      logger.warn("No search fields configured for featuretype: {}", tmFeatureType.getName());
      throw new UnsupportedOperationException(
          "No search fields configured for featuretype: %s".formatted(tmFeatureType.getName()));
    }

    // set fields while filtering out hidden fields
    List<String> searchFields =
        tmFeatureType.getSettings().getSearchFields().stream()
            .filter(s -> !tmFeatureType.getSettings().getHideAttributes().contains(s))
            .toList();
    List<String> displayFields =
        tmFeatureType.getSettings().getSearchDisplayFields().stream()
            .filter(s -> !tmFeatureType.getSettings().getHideAttributes().contains(s))
            .toList();

    searchIndex
        .searchFieldsUsed(searchFields)
        .searchDisplayFieldsUsed(displayFields)
        .status(SearchIndex.Status.INDEXING);

    if (searchFields.isEmpty()) {
      logger.info("No valid search fields configured for featuretype: {}", tmFeatureType.getName());
      searchIndex.setStatus(SearchIndex.Status.ERROR);
      throw new UnsupportedOperationException(
          "No valid search fields configured for featuretype: %s"
              .formatted(tmFeatureType.getName()));
    }

    // add search and display properties to query
    Set<String> propertyNames = new HashSet<>();
    // always add primary key and default geometry to geotools query
    propertyNames.add(tmFeatureType.getPrimaryKeyAttribute());
    propertyNames.add(tmFeatureType.getDefaultGeometryAttribute());
    propertyNames.addAll(searchFields);

    final boolean hasDisplayFields = !displayFields.isEmpty();
    if (hasDisplayFields) {
      propertyNames.addAll(displayFields);
    }

    clearIndexForLayer(searchIndex.getId());

    logger.info(
        "Indexing started for index id: {}, feature type: {}",
        searchIndex.getId(),
        tmFeatureType.getName());
    // collect features to index
    SimpleFeatureSource fs = featureSourceFactoryHelper.openGeoToolsFeatureSource(tmFeatureType);
    Query q = new Query(fs.getName().toString());
    // filter out any hidden properties (there should be none though)
    tmFeatureType.getSettings().getHideAttributes().forEach(propertyNames::remove);
    q.setPropertyNames(List.copyOf(propertyNames));
    q.setStartIndex(0);
    // TODO: make maxFeatures configurable? perhaps for WFS sources?
    // q.setMaxFeatures(Integer.MAX_VALUE);
    logger.trace("Indexing query: {}", q);
    SimpleFeatureCollection simpleFeatureCollection = fs.getFeatures(q);
    final int total = simpleFeatureCollection.size();
    List<FeatureIndexingDocument> docsBatch = new ArrayList<>(SOLR_BATCH_SIZE);
    // TODO this does not currently batch/page the feature source query, this doesn't seem to be an
    //   issue for now but could be if the feature source is very large or slow e.g. WFS
    UpdateResponse updateResponse;
    try (SimpleFeatureIterator iterator = simpleFeatureCollection.features()) {
      int indexCounter = 0;
      while (iterator.hasNext()) {
        indexCounter++;
        SimpleFeature feature = iterator.next();
        // note that this will create a unique document
        FeatureIndexingDocument doc =
            new FeatureIndexingDocument(feature.getID(), searchIndex.getId());
        List<String> searchValues = new ArrayList<>();
        List<String> displayValues = new ArrayList<>();
        propertyNames.forEach(
            propertyName -> {
              Object value = feature.getAttribute(propertyName);
              if (value != null) {
                if (value instanceof Geometry) {
                  doc.setGeometry(GeometryProcessor.processGeometry(value, true, true, null));
                } else {
                  // when display and/or search fields are configured, add the value to the search
                  // and/or display field otherwise add the value to the search and display field
                  if (searchFields.contains(propertyName)) {
                    searchValues.add(value.toString());
                  }
                  if (hasDisplayFields) {
                    if (displayFields.contains(propertyName)) {
                      displayValues.add(value.toString());
                    }
                  }
                }
              }
            });
        doc.setSearchFields(searchValues.toArray(new String[searchFields.size() + 2]));
        doc.setDisplayFields(displayValues.toArray(new String[0]));
        docsBatch.add(doc);
        if (indexCounter % SOLR_BATCH_SIZE == 0) {
          updateResponse = solrClient.addBeans(docsBatch);
          logger.info(
              "Added {} documents of {} to index, result status: {}",
              indexCounter,
              total,
              updateResponse.getStatus());
          docsBatch.clear();
        }
      }
    }
    if (!docsBatch.isEmpty()) {
      updateResponse = solrClient.addBeans(docsBatch);
      logger.info("Added last {} documents of {} to index", docsBatch.size(), total);
      logger.debug("Update response status: {}", updateResponse.getStatus());
    }
    final Instant end = Instant.now();
    Duration processTime = Duration.between(start, end).abs();
    logger.info(
        "Indexing finished for index id: {}, featuretype: {} at {} in {}",
        searchIndex.getId(),
        tmFeatureType.getName(),
        end,
        processTime);
    searchIndex.setComment(
        "Indexed %s features in %s.%s seconds, started at %s"
            .formatted(total, processTime.getSeconds(), processTime.getNano(), start));

    searchIndex.setLastIndexed(end.atOffset(ZoneId.systemDefault().getRules().getOffset(end)));
    searchIndex.setStatus(SearchIndex.Status.INDEXED);

    updateResponse = this.solrClient.commit();
    logger.debug("Update response status: {}", updateResponse.getStatus());
  }

  /**
   * Clear the index for a layer.
   *
   * @param searchLayerId the layer id
   * @throws IOException if an I/O error occurs
   * @throws SolrServerException if a Solr error occurs
   */
  public void clearIndexForLayer(@NotNull Long searchLayerId)
      throws IOException, SolrServerException {
    QueryResponse response =
        solrClient.query(
            new SolrQuery("exists(query(" + SEARCH_LAYER + ":" + searchLayerId + "))"));
    if (response.getResults().getNumFound() > 0) {
      logger.info("Clearing index for searchLayer {}", searchLayerId);
      UpdateResponse updateResponse = solrClient.deleteByQuery(SEARCH_LAYER + ":" + searchLayerId);
      logger.debug("Update response status: {}", updateResponse.getStatus());
      updateResponse = solrClient.commit();
      logger.debug("Update response status: {}", updateResponse.getStatus());
    } else {
      logger.info("No index to clear for layer {}", searchLayerId);
    }
  }

  /**
   * Search in the index for a layer. The given query is augmented to filter on the {@code
   * solrLayerId}.
   *
   * @param searchIndex the search index
   * @param solrQuery the query, when {@code null} or empty, the query is set to {@code *} (match
   *     all)
   * @param start the start index, starting at 0
   * @param numResultsToReturn the number of results to return
   * @return the documents
   * @throws IOException if an I/O error occurs
   * @throws SolrServerException if a Solr error occurs
   */
  public SearchResponse findInIndex(
      @NotNull SearchIndex searchIndex, String solrQuery, int start, int numResultsToReturn)
      throws IOException, SolrServerException, SolrException {
    logger.info("Find in index for {}", searchIndex.getId());
    if (null == solrQuery || solrQuery.isBlank()) {
      solrQuery = "*";
    }
    // TODO We could escape special/syntax characters, but that also prevents using keys like ~ and
    // *
    // solrQuery = ClientUtils.escapeQueryChars(solrQuery);

    final SolrQuery query =
        new SolrQuery(INDEX_SEARCH_FIELD + ":" + solrQuery)
            .setShowDebugInfo(logger.isDebugEnabled())
            .setTimeAllowed(SOLR_TIMEOUT)
            .setIncludeScore(true)
            .setFields(SEARCH_ID_FIELD, INDEX_DISPLAY_FIELD, INDEX_GEOM_FIELD)
            .addFilterQuery(SEARCH_LAYER + ":" + searchIndex.getId())
            .setSort("score", SolrQuery.ORDER.desc)
            .addSort(SEARCH_ID_FIELD, SolrQuery.ORDER.asc)
            .setRows(numResultsToReturn)
            .setStart(start);
    query.set("q.op", "AND");
    logger.debug("Solr query: {}", query);

    final QueryResponse response = solrClient.query(query);
    logger.debug("response: {}", response);

    final SolrDocumentList solrDocumentList = response.getResults();
    logger.debug("Found {} solr documents", solrDocumentList.getNumFound());
    final SearchResponse searchResponse =
        new SearchResponse()
            .total(solrDocumentList.getNumFound())
            .start(response.getResults().getStart())
            .maxScore(solrDocumentList.getMaxScore());
    response
        .getResults()
        .forEach(
            solrDocument -> {
              List<String> displayValues =
                  solrDocument.getFieldValues(INDEX_DISPLAY_FIELD).stream()
                      .map(Object::toString)
                      .toList();
              searchResponse.addDocumentsItem(
                  new SearchDocument()
                      .fid(solrDocument.getFieldValue(SEARCH_ID_FIELD).toString())
                      .geometry(solrDocument.getFieldValue(INDEX_GEOM_FIELD).toString())
                      .displayValues(displayValues));
            });

    return searchResponse;
  }

  /**
   * Programmatically create (part of) the schema if it does not exist. Only checks for the
   * existence of the search layer {@link Constants#SEARCH_LAYER}.
   *
   * @throws SolrServerException if a Solr error occurs
   * @throws IOException if an I/O error occurs
   */
  private void createSchemaIfNotExists() throws SolrServerException, IOException {
    SchemaRequest.Field fieldCheck = new SchemaRequest.Field(SEARCH_LAYER);
    boolean schemaExists = true;
    try {
      SchemaResponse.FieldResponse isField = fieldCheck.process(solrClient);
      logger.debug("Field type {} exists", isField.getField());
    } catch (Exception e) {
      logger.debug(e.getLocalizedMessage());
      logger.info("Field type {} does not exist, creating it", SEARCH_LAYER);
      schemaExists = false;
    }

    if (schemaExists) {
      return;
    }

    logger.info("Creating Solr field type {}", SEARCH_LAYER);
    SchemaRequest.AddField schemaRequest =
        new SchemaRequest.AddField(
            Map.of(
                "name", SEARCH_LAYER,
                "type", "string",
                "indexed", true,
                "stored", true,
                "multiValued", false,
                "required", true,
                "uninvertible", false));
    schemaRequest.process(solrClient);

    logger.info("Creating Solr field type {}", INDEX_GEOM_FIELD);
    // TODO https://b3partners.atlassian.net/browse/HTM-1091
    //  this should be a spatial field type using ("type", "location_rpt")
    //  but that requires some more work
    SchemaRequest.AddField schemaRequestGeom =
        new SchemaRequest.AddField(
            Map.of(
                "name", INDEX_GEOM_FIELD,
                "type", "string",
                "indexed", false,
                "stored", true,
                "multiValued", false));
    schemaRequestGeom.process(solrClient);
  }

  /**
   * Close the wrapped Solr client.
   *
   * @throws IOException if an I/O error occurs
   */
  @Override
  public void close() throws IOException {
    if (null != this.solrClient) this.solrClient.close();
  }
}
