/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.solr;

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
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.geotools.processing.GeometryProcessor;
import org.tailormap.api.persistence.SearchIndex;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.repository.SearchIndexRepository;
import org.tailormap.api.util.Constants;
import org.tailormap.api.viewer.model.SearchDocument;
import org.tailormap.api.viewer.model.SearchResponse;

/**
 * Solr utility/wrapper class. This class provides methods to add or update a full-text feature type
 * index for a layer, find in the index for a layer, and clear the index for a layer. It also
 * provides a method to close the Solr client as well as automatically closing the client when used
 * in a try-with-resources.
 */
public class SolrHelper implements AutoCloseable, Constants {
  public static final int SOLR_BATCH_SIZE = 1000;
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  // milliseconds
  private static final int SOLR_TIMEOUT = 7000;
  private final SolrClient solrClient;

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
   * @param featureSourceFactoryHelper the feature source factory helper
   * @param searchIndexRepository the search index repository, so we can save the {@code
   *     searchIndex}
   * @throws IOException if an I/O error occurs
   * @throws SolrServerException if a Solr error occurs
   * @return the possibly updated {@code searchIndex} object
   */
  @SuppressWarnings("FromTemporalAccessor")
  public SearchIndex addFeatureTypeIndex(
      @NotNull SearchIndex searchIndex,
      @NotNull TMFeatureType tmFeatureType,
      @NotNull FeatureSourceFactoryHelper featureSourceFactoryHelper,
      @NotNull SearchIndexRepository searchIndexRepository)
      throws IOException, SolrServerException {

    createSchemaIfNotExists();

    final Instant startedAt = Instant.now();

    if (null == searchIndex.getSearchFieldsUsed()) {
      logger.warn(
          "No search fields configured for search index: {}, bailing out.", searchIndex.getName());
      return searchIndexRepository.save(
          searchIndex
              .setStatus(SearchIndex.Status.ERROR)
              .setComment("No search fields configured"));
    }

    // set fields while filtering out hidden fields
    List<String> searchFields =
        searchIndex.getSearchFieldsUsed().stream()
            .filter(s -> !tmFeatureType.getSettings().getHideAttributes().contains(s))
            .toList();
    List<String> displayFields =
        searchIndex.getSearchDisplayFieldsUsed().stream()
            .filter(s -> !tmFeatureType.getSettings().getHideAttributes().contains(s))
            .toList();

    if (searchFields.isEmpty()) {
      logger.warn(
          "No valid search fields configured for featuretype: {}, bailing out.",
          tmFeatureType.getName());
      return searchIndexRepository.save(
          searchIndex
              .setStatus(SearchIndex.Status.ERROR)
              .setComment("No search fields configured"));
    }

    // add search and display properties to query
    Set<String> propertyNames = new HashSet<>();
    // always add primary key and default geometry to geotools query
    propertyNames.add(tmFeatureType.getPrimaryKeyAttribute());
    propertyNames.add(tmFeatureType.getDefaultGeometryAttribute());
    propertyNames.addAll(searchFields);

    if (!displayFields.isEmpty()) {
      propertyNames.addAll(displayFields);
    }

    clearIndexForLayer(searchIndex.getId());

    logger.info(
        "Indexing started for index id: {}, feature type: {}",
        searchIndex.getId(),
        tmFeatureType.getName());
    searchIndex = searchIndexRepository.save(searchIndex.setStatus(SearchIndex.Status.INDEXING));

    // collect features to index
    SimpleFeatureSource fs = featureSourceFactoryHelper.openGeoToolsFeatureSource(tmFeatureType);
    Query q = new Query(fs.getName().toString());
    // filter out any hidden properties (there should be none though)
    tmFeatureType.getSettings().getHideAttributes().forEach(propertyNames::remove);
    q.setPropertyNames(List.copyOf(propertyNames));
    q.setStartIndex(0);
    // TODO: make maxFeatures configurable?
    // q.setMaxFeatures(Integer.MAX_VALUE);
    logger.trace("Indexing query: {}", q);
    SimpleFeatureCollection simpleFeatureCollection = fs.getFeatures(q);
    final int total = simpleFeatureCollection.size();
    List<FeatureIndexingDocument> docsBatch = new ArrayList<>(SOLR_BATCH_SIZE);
    // TODO this does not currently batch/page the feature source query, this doesn't seem to be an
    //   issue for now but could be if the feature source is very, very large or slow
    UpdateResponse updateResponse;
    int indexCounter = 0;
    int indexSkippedCounter = 0;
    try (SimpleFeatureIterator iterator = simpleFeatureCollection.features()) {
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
                if (value instanceof Geometry
                    && propertyName.equals(tmFeatureType.getDefaultGeometryAttribute())) {
                  doc.setGeometry(GeometryProcessor.processGeometry(value, true, true, null));
                } else {
                  if (searchFields.contains(propertyName)) {
                    searchValues.add(value.toString());
                  }
                  if (displayFields.contains(propertyName)) {
                    displayValues.add(value.toString());
                  }
                }
              }
            });
        if (searchValues.isEmpty() || displayValues.isEmpty()) {
          // this is a record/document that can either not be found or not be displayed
          logger.warn(
              "No search or display values found for feature: {} in featuretype: {}, skipped for indexing",
              feature.getID(),
              tmFeatureType.getName());
          indexSkippedCounter++;
        } else {
          doc.setSearchFields(searchValues.toArray(new String[0]));
          doc.setDisplayFields(displayValues.toArray(new String[0]));
          docsBatch.add(doc);
        }

        if (indexCounter % SOLR_BATCH_SIZE == 0) {
          updateResponse = solrClient.addBeans(docsBatch);
          logger.info(
              "Added {} documents of {} to index, result status: {}",
              indexCounter - indexSkippedCounter,
              total,
              updateResponse.getStatus());
          docsBatch.clear();
        }
      }
    } finally {
      if (fs.getDataStore() != null) fs.getDataStore().dispose();
    }

    if (!docsBatch.isEmpty()) {
      solrClient.addBeans(docsBatch);
      logger.info("Added last {} documents of {} to index", docsBatch.size(), total);
    }
    final Instant finishedAt = Instant.now();
    Duration processTime = Duration.between(startedAt, finishedAt).abs();
    logger.info(
        "Indexing finished for index id: {}, featuretype: {} at {} in {}",
        searchIndex.getId(),
        tmFeatureType.getName(),
        finishedAt,
        processTime);
    updateResponse = this.solrClient.commit();
    logger.debug("Update response commit status: {}", updateResponse.getStatus());

    if (indexSkippedCounter > 0) {
      logger.warn(
          "{} features were skipped because no search or display values were found.",
          indexSkippedCounter);
      searchIndex =
          searchIndex.setComment(
              "Indexed %s features in %s.%s seconds, started at %s. %s features were skipped because no search or display values were found."
                  .formatted(
                      total,
                      processTime.getSeconds(),
                      processTime.getNano(),
                      startedAt,
                      indexSkippedCounter));
    } else {
      searchIndex =
          searchIndex.setComment(
              "Indexed %s features in %s.%s seconds, started at %s."
                  .formatted(total, processTime.getSeconds(), processTime.getNano(), startedAt));
    }

    return searchIndexRepository.save(
        searchIndex
            .setLastIndexed(
                finishedAt.atOffset(ZoneId.systemDefault().getRules().getOffset(finishedAt)))
            .setStatus(SearchIndex.Status.INDEXED));
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
      logger.debug("Delete response status: {}", updateResponse.getStatus());
      updateResponse = solrClient.commit();
      logger.debug("Commit response status: {}", updateResponse.getStatus());
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
    // TODO We could escape special/syntax characters, but that also prevents using
    //      keys like ~ and *
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
    SchemaRequest.AddField searchLayerSchemaRequest =
        new SchemaRequest.AddField(
            Map.of(
                "name", SEARCH_LAYER,
                "type", "string",
                "indexed", true,
                "stored", true,
                "multiValued", false,
                "required", true,
                "uninvertible", false));
    searchLayerSchemaRequest.process(solrClient);

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

    logger.info("Creating Solr field type {}", INDEX_DISPLAY_FIELD);
    SchemaRequest.AddField displayFieldSchemaRequest =
        new SchemaRequest.AddField(
            Map.of(
                "name", INDEX_DISPLAY_FIELD,
                "type", "text_general",
                "indexed", false,
                "stored", true,
                "multiValued", true,
                "required", true,
                "uninvertible", false));
    displayFieldSchemaRequest.process(solrClient);

    logger.info("Creating Solr field type {}", INDEX_SEARCH_FIELD);
    SchemaRequest.AddField indexFieldSchemaRequest =
        new SchemaRequest.AddField(
            Map.of(
                "name", INDEX_SEARCH_FIELD,
                "type", "text_general",
                "indexed", true,
                "stored", true,
                "multiValued", true,
                "required", true,
                "uninvertible", false));
    indexFieldSchemaRequest.process(solrClient);
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
