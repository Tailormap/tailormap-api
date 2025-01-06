/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.solr;

import static org.tailormap.api.scheduling.IndexTask.INDEX_KEY;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrResponse;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.request.schema.FieldTypeDefinition;
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
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.tailormap.api.admin.model.SearchIndexSummary;
import org.tailormap.api.admin.model.TaskProgressEvent;
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.geotools.processing.GeometryProcessor;
import org.tailormap.api.persistence.SearchIndex;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.repository.SearchIndexRepository;
import org.tailormap.api.scheduling.TaskType;
import org.tailormap.api.util.Constants;
import org.tailormap.api.viewer.model.SearchDocument;
import org.tailormap.api.viewer.model.SearchResponse;

/**
 * Solr utility/wrapper class. This class provides methods to add or update a full-text feature type index for a layer,
 * find in the index for a layer, and clear the index for a layer. It also provides a method to close the Solr client as
 * well as automatically closing the client when used in a try-with-resources.
 */
public class SolrHelper implements AutoCloseable, Constants {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /** the Solr field type name geometry fields: {@value #SOLR_SPATIAL_FIELDNAME}. */
  private static final String SOLR_SPATIAL_FIELDNAME = "tm_geometry_rpt";

  private final SolrClient solrClient;

  /** the Solr search field definition requests for Tailormap. */
  private final Map<String, SchemaRequest.AddField> solrSearchFields = Map.of(
      SEARCH_LAYER,
          new SchemaRequest.AddField(Map.of(
              "name", SEARCH_LAYER,
              "type", "string",
              "indexed", true,
              "stored", true,
              "multiValued", false,
              "required", true,
              "uninvertible", false)),
      INDEX_GEOM_FIELD,
          new SchemaRequest.AddField(
              Map.of("name", INDEX_GEOM_FIELD, "type", SOLR_SPATIAL_FIELDNAME, "stored", true)),
      INDEX_SEARCH_FIELD,
          new SchemaRequest.AddField(Map.of(
              "name", INDEX_SEARCH_FIELD,
              "type", "text_general",
              "indexed", true,
              "stored", true,
              "multiValued", true,
              "required", true,
              "uninvertible", false)),
      INDEX_DISPLAY_FIELD,
          new SchemaRequest.AddField(Map.of(
              "name", INDEX_DISPLAY_FIELD,
              "type", "text_general",
              "indexed", false,
              "stored", true,
              "multiValued", true,
              "required", true,
              "uninvertible", false)));

  private int solrQueryTimeout = 7000;
  private int solrBatchSize = 1000;
  private String solrGeometryValidationRule = "repairBuffer0";

  /**
   * Create a configured {@code SolrHelper} object.
   *
   * @param solrClient the Solr client, this will be closed when this class is closed
   */
  public SolrHelper(@NotNull SolrClient solrClient) {
    this.solrClient = solrClient;
  }

  /**
   * Configure this {@code SolrHelper} with a query timeout .
   *
   * @param solrQueryTimeout the query timeout in seconds
   */
  public SolrHelper withQueryTimeout(
      @Positive(message = "Must use a positive integer for query timeout") int solrQueryTimeout) {
    this.solrQueryTimeout = solrQueryTimeout * 1000;
    return this;
  }

  /**
   * Configure this {@code SolrHelper} with a batch size for submitting documents to the Solr instance.
   *
   * @param solrBatchSize the batch size for indexing, must be greater than 0
   */
  public SolrHelper withBatchSize(@Positive(message = "Must use a positive integer for batching") int solrBatchSize) {
    this.solrBatchSize = solrBatchSize;
    return this;
  }

  /**
   * Configure this {@code SolrHelper} to create a geometry field in Solr using the specified validation rule.
   *
   * @see <a
   *     href="https://locationtech.github.io/spatial4j/apidocs/org/locationtech/spatial4j/context/jts/ValidationRule.html">ValidationRule</a>
   * @param solrGeometryValidationRule any of {@code "error", "none", "repairBuffer0", "repairConvexHull"}
   */
  public SolrHelper withGeometryValidationRule(@NonNull String solrGeometryValidationRule) {
    if (List.of("error", "none", "repairBuffer0", "repairConvexHull").contains(solrGeometryValidationRule)) {
      logger.trace("Setting geometry validation rule for Solr geometry field to {}", solrGeometryValidationRule);
      this.solrGeometryValidationRule = solrGeometryValidationRule;
    }
    return this;
  }

  /**
   * Add or update a feature type index for a layer.
   *
   * @param searchIndex the search index config
   * @param tmFeatureType the feature type
   * @param featureSourceFactoryHelper the feature source factory helper
   * @param searchIndexRepository the search index repository, so we can save the {@code searchIndex}
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
    // use a dummy/logging listener when not given
    Consumer<TaskProgressEvent> progressListener = (event) -> logger.debug("Progress event: {}", event);

    return this.addFeatureTypeIndex(
        searchIndex, tmFeatureType, featureSourceFactoryHelper, searchIndexRepository, progressListener, null);
  }

  /**
   * Add or update a feature type index for a layer.
   *
   * @param searchIndex the search index config
   * @param tmFeatureType the feature type
   * @param featureSourceFactoryHelper the feature source factory helper
   * @param searchIndexRepository the search index repository, so we can save the {@code searchIndex}
   * @param progressListener the progress listener callback
   * @param taskUuid the task UUID, when {@code null} we will attempt to use the UUID from the
   *     {@code searchIndex#getSchedule()}
   * @throws IOException if an I/O error occurs
   * @throws SolrServerException if a Solr error occurs
   * @return the possibly updated {@code searchIndex} object
   */
  @SuppressWarnings("FromTemporalAccessor")
  public SearchIndex addFeatureTypeIndex(
      @NotNull SearchIndex searchIndex,
      @NotNull TMFeatureType tmFeatureType,
      @NotNull FeatureSourceFactoryHelper featureSourceFactoryHelper,
      @NotNull SearchIndexRepository searchIndexRepository,
      @NotNull Consumer<TaskProgressEvent> progressListener,
      @Nullable UUID taskUuid)
      throws IOException, SolrServerException {

    createSchemaIfNotExists();

    final Instant startedAt = Instant.now();
    final OffsetDateTime startedAtOffset =
        startedAt.atOffset(ZoneId.systemDefault().getRules().getOffset(startedAt));

    if (null == taskUuid && null != searchIndex.getSchedule()) {
      // this can be the case when this method is called directly such as when creating
      // the test data. This in itself is not a big problem; it just means that the uuid
      // in any progress events will be null (for that call).
      taskUuid = searchIndex.getSchedule().getUuid();
    }

    SearchIndexSummary summary =
        new SearchIndexSummary().startedAt(startedAtOffset).total(0).duration(0.0);

    if (null == searchIndex.getSearchFieldsUsed()) {
      logger.warn("No search fields configured for search index: {}, bailing out.", searchIndex.getName());
      return searchIndexRepository.save(searchIndex
          .setStatus(SearchIndex.Status.ERROR)
          .setSummary(summary.errorMessage("No search fields configured")));
    }

    TaskProgressEvent taskProgressEvent = new TaskProgressEvent()
        .type(TaskType.INDEX.getValue())
        .uuid(taskUuid)
        .startedAt(startedAtOffset)
        .progress(0)
        .taskData(Map.of(INDEX_KEY, searchIndex.getId()));
    progressListener.accept(taskProgressEvent);

    // set fields while filtering out hidden fields
    List<String> searchFields = searchIndex.getSearchFieldsUsed().stream()
        .filter(s -> !tmFeatureType.getSettings().getHideAttributes().contains(s))
        .toList();
    List<String> displayFields = searchIndex.getSearchDisplayFieldsUsed().stream()
        .filter(s -> !tmFeatureType.getSettings().getHideAttributes().contains(s))
        .toList();

    if (searchFields.isEmpty()) {
      logger.warn("No valid search fields configured for featuretype: {}, bailing out.", tmFeatureType.getName());
      return searchIndexRepository.save(searchIndex
          .setStatus(SearchIndex.Status.ERROR)
          .setSummary(summary.errorMessage("No search fields configured")));
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
        "Indexing started for index id: {}, feature type: {}", searchIndex.getId(), tmFeatureType.getName());
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
    List<FeatureIndexingDocument> docsBatch = new ArrayList<>(solrBatchSize);
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
        FeatureIndexingDocument doc = new FeatureIndexingDocument(feature.getID(), searchIndex.getId());
        List<String> searchValues = new ArrayList<>();
        List<String> displayValues = new ArrayList<>();
        propertyNames.forEach(propertyName -> {
          Object value = feature.getAttribute(propertyName);
          if (value != null) {
            if (value instanceof Geometry
                && propertyName.equals(tmFeatureType.getDefaultGeometryAttribute())) {
              // We could use GeoJSON, but WKT is more compact and that would also incur a
              // change to the API
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
          logger.trace(
              "No search or display values found for feature: {} in featuretype: {}, skipped for indexing",
              feature.getID(),
              tmFeatureType.getName());
          indexSkippedCounter++;
        } else {
          doc.setSearchFields(searchValues.toArray(new String[0]));
          doc.setDisplayFields(displayValues.toArray(new String[0]));
          docsBatch.add(doc);
        }

        if (indexCounter % solrBatchSize == 0) {
          updateResponse = solrClient.addBeans(docsBatch, solrQueryTimeout);
          logger.info(
              "Added {} documents of {} to index, result status: {}",
              indexCounter - indexSkippedCounter,
              total,
              updateResponse.getStatus());
          progressListener.accept(
              taskProgressEvent.total(total).progress((indexCounter - indexSkippedCounter)));
          docsBatch.clear();
        }
      }
    } finally {
      if (fs.getDataStore() != null) fs.getDataStore().dispose();
    }

    if (!docsBatch.isEmpty()) {
      solrClient.addBeans(docsBatch, solrQueryTimeout);
      logger.info("Added last {} documents of {} to index", docsBatch.size(), total);
      progressListener.accept(taskProgressEvent
          .progress((indexCounter - indexSkippedCounter))
          .total(total));
    }
    final Instant finishedAt = Instant.now();
    final OffsetDateTime finishedAtOffset =
        finishedAt.atOffset(ZoneId.systemDefault().getRules().getOffset(finishedAt));
    Duration processTime = Duration.between(startedAt, finishedAt).abs();
    logger.info(
        "Indexing finished for index id: {}, featuretype: {} at {} in {}",
        searchIndex.getId(),
        tmFeatureType.getName(),
        finishedAtOffset,
        processTime);
    updateResponse = this.solrClient.commit();
    logger.trace("Update response commit status: {}", updateResponse.getStatus());

    if (indexSkippedCounter > 0) {
      logger.warn(
          "{} features were skipped because no search or display values were found.", indexSkippedCounter);
    }

    return searchIndexRepository.save(searchIndex
        .setLastIndexed(finishedAtOffset)
        .setStatus(SearchIndex.Status.INDEXED)
        .setSummary(summary.total(total)
            .skippedCounter(indexSkippedCounter)
            .duration(BigDecimal.valueOf(processTime.getSeconds())
                .add(BigDecimal.valueOf(processTime.getNano(), 9))
                .doubleValue())
            .errorMessage(null)));
  }

  /**
   * Clear the index for a layer.
   *
   * @param searchLayerId the layer id
   * @throws IOException if an I/O error occurs
   * @throws SolrServerException if a Solr error occurs
   */
  public void clearIndexForLayer(@NotNull Long searchLayerId) throws IOException, SolrServerException {

    QueryResponse response =
        solrClient.query(new SolrQuery("exists(query(" + SEARCH_LAYER + ":" + searchLayerId + "))"));
    if (response.getResults().getNumFound() > 0) {
      logger.info("Clearing index for searchLayer {}", searchLayerId);
      UpdateResponse updateResponse = solrClient.deleteByQuery(SEARCH_LAYER + ":" + searchLayerId);
      logger.trace("Delete response status: {}", updateResponse.getStatus());
      updateResponse = solrClient.commit();
      logger.trace("Commit response status: {}", updateResponse.getStatus());
    } else {
      logger.info("No index to clear for layer {}", searchLayerId);
    }
  }

  /**
   * Search in the index for a layer. The given query is augmented to filter on the {@code solrLayerId}.
   *
   * @param searchIndex the search index
   * @param solrQuery the query, when {@code null} or empty, the query is set to {@code *} (match all)
   * @param solrPoint the point to search around, in (x y) format
   * @param solrDistance the distance to search around the point in Solr distance units (kilometers)
   * @param start the start index, starting at 0
   * @param numResultsToReturn the number of results to return
   * @return the documents
   * @throws IOException if an I/O error occurs
   * @throws SolrServerException if a Solr error occurs
   */
  public SearchResponse findInIndex(
      @NotNull SearchIndex searchIndex,
      String solrQuery,
      String solrFilterQuery,
      String solrPoint,
      Double solrDistance,
      int start,
      int numResultsToReturn)
      throws IOException, SolrServerException, SolrException {

    if (null == solrQuery || solrQuery.isBlank()) {
      solrQuery = "*";
    }

    logger.info("Query index for '{}' in {} (id {})", solrQuery, searchIndex.getName(), searchIndex.getId());

    // TODO We could escape special/syntax characters, but that also prevents using
    //      keys like ~ and *
    // solrQuery = ClientUtils.escapeQueryChars(solrQuery);

    final SolrQuery query = new SolrQuery(INDEX_SEARCH_FIELD + ":" + solrQuery)
        .setShowDebugInfo(logger.isDebugEnabled())
        .setTimeAllowed(solrQueryTimeout)
        .setIncludeScore(true)
        .setFields(SEARCH_ID_FIELD, INDEX_DISPLAY_FIELD, INDEX_GEOM_FIELD)
        .addFilterQuery(SEARCH_LAYER + ":" + searchIndex.getId())
        .setSort("score", SolrQuery.ORDER.desc)
        .addSort(SEARCH_ID_FIELD, SolrQuery.ORDER.asc)
        .setRows(numResultsToReturn)
        .setStart(start);

    if (null != solrFilterQuery && !solrFilterQuery.isBlank()) {
      query.addFilterQuery(solrFilterQuery);
    }
    if (null != solrPoint && null != solrDistance) {
      if (null == solrFilterQuery
          || !(solrFilterQuery.startsWith("{!geofilt") || solrFilterQuery.startsWith("{!bbox"))) {
        query.addFilterQuery("{!geofilt sfield=" + INDEX_GEOM_FIELD + "}");
      }
      query.add("pt", solrPoint);
      query.add("d", solrDistance.toString());
    }
    query.set("q.op", "AND");
    logger.info("Solr query: {}", query);

    final QueryResponse response = solrClient.query(query);
    logger.trace("response: {}", response);

    final SolrDocumentList solrDocumentList = response.getResults();
    logger.debug("Found {} solr documents", solrDocumentList.getNumFound());
    final SearchResponse searchResponse = new SearchResponse()
        .total(solrDocumentList.getNumFound())
        .start(response.getResults().getStart())
        .maxScore(solrDocumentList.getMaxScore());
    response.getResults().forEach(solrDocument -> {
      List<String> displayValues = solrDocument.getFieldValues(INDEX_DISPLAY_FIELD).stream()
          .map(Object::toString)
          .toList();
      searchResponse.addDocumentsItem(new SearchDocument()
          .fid(solrDocument.getFieldValue(SEARCH_ID_FIELD).toString())
          .geometry(solrDocument.getFieldValue(INDEX_GEOM_FIELD).toString())
          .displayValues(displayValues));
    });

    return searchResponse;
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

  private boolean checkSchemaIfFieldExists(String fieldName) {
    SchemaRequest.Field fieldCheck = new SchemaRequest.Field(fieldName);
    try {
      SchemaResponse.FieldResponse isField = fieldCheck.process(solrClient);
      logger.debug("Field {} exists", isField.getField());
      return true;
    } catch (SolrServerException | BaseHttpSolrClient.RemoteSolrException e) {
      logger.debug("Field {} does not exist or could not be retrieved. Assuming it does not exist.", fieldName);
    } catch (IOException e) {
      logger.error("Tried getting field: {}, but failed.", fieldName, e);
    }
    return false;
  }

  /**
   * @param fieldName the name of the field to create
   * @throws SolrServerException if a Solr error occurs
   * @throws IOException if an I/O error occurs
   */
  private void createSchemaFieldIfNotExists(String fieldName) throws SolrServerException, IOException {
    if (!checkSchemaIfFieldExists(fieldName)) {
      logger.info("Creating Solr field {}.", fieldName);
      SchemaRequest.AddField schemaRequest = solrSearchFields.get(fieldName);
      SolrResponse response = schemaRequest.process(solrClient);
      logger.debug("Field type {} created", response);
      solrClient.commit();
    }
  }

  /** Programmatically create the schema if it does not exist. */
  private void createSchemaIfNotExists() {
    solrSearchFields.forEach((key, value) -> {
      try {
        if (key.equals(INDEX_GEOM_FIELD)) {
          createGeometryFieldTypeIfNotExists();
        }
        createSchemaFieldIfNotExists(key);
      } catch (SolrServerException | IOException e) {
        logger.error(
            "Error creating schema field: {} indexing may fail. Details: {}",
            key,
            e.getLocalizedMessage(),
            e);
      }
    });
  }

  private void createGeometryFieldTypeIfNotExists() throws SolrServerException, IOException {
    SchemaRequest.FieldType fieldTypeCheck = new SchemaRequest.FieldType(SOLR_SPATIAL_FIELDNAME);
    try {
      SchemaResponse.FieldTypeResponse isFieldType = fieldTypeCheck.process(solrClient);
      logger.debug("Field type {} exists", isFieldType.getFieldType());
      return;
    } catch (SolrServerException | BaseHttpSolrClient.RemoteSolrException e) {
      logger.debug(
          "Field type {} does not exist or could not be retrieved. Assuming it does not exist.",
          SOLR_SPATIAL_FIELDNAME);
    } catch (IOException e) {
      logger.error("Tried getting field type: {}, but failed.", SOLR_SPATIAL_FIELDNAME, e);
    }

    logger.info(
        "Creating Solr field type for {} with validation rule {}",
        SOLR_SPATIAL_FIELDNAME,
        solrGeometryValidationRule);
    FieldTypeDefinition spatialFieldTypeDef = new FieldTypeDefinition();
    Map<String, Object> spatialFieldAttributes = new HashMap<>(Map.of(
        "name", SOLR_SPATIAL_FIELDNAME,
        "class", "solr.SpatialRecursivePrefixTreeFieldType",
        "spatialContextFactory", "JTS",
        "geo", false,
        "distanceUnits", "kilometers",
        "distCalculator", "cartesian",
        "format", "WKT",
        "autoIndex", true,
        "distErrPct", "0.025",
        "maxDistErr", "0.001"));
    spatialFieldAttributes.putAll(Map.of(
        "prefixTree",
        "packedQuad",
        // see
        // https://locationtech.github.io/spatial4j/apidocs/org/locationtech/spatial4j/context/jts/ValidationRule.html
        "validationRule",
        this.solrGeometryValidationRule,
        // NOTE THE ODDITY in coordinate order of "worldBounds",
        // "ENVELOPE(minX, maxX, maxY, minY)"
        "worldBounds",
        // webmercator / EPSG:3857 projected bounds
        "ENVELOPE(-20037508.34, 20037508.34, 20048966.1, -20048966.1)"
        // Amersfoort/RD new / EPSG:28992 projected bounds
        // "ENVELOPE(482.06, 284182.97, 637049.52, 306602.42)"
        ));
    spatialFieldTypeDef.setAttributes(spatialFieldAttributes);
    SchemaRequest.AddFieldType spatialFieldType = new SchemaRequest.AddFieldType(spatialFieldTypeDef);
    spatialFieldType.process(solrClient);
    solrClient.commit();
  }
}
