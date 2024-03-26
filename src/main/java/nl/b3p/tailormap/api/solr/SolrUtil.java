/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.solr;

import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nl.b3p.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.util.Constants;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.client.solrj.response.schema.SchemaResponse;
import org.apache.solr.common.SolrDocumentList;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Solr utility class. This class provides methods to add or update a full-text feature type index
 * for a layer, find in the index for a layer, and clear the index for a layer. It also provides a
 * method to close the Solr client as well as automatically closing the client when used in a
 * try-with-resources.
 */
public class SolrUtil implements AutoCloseable, Constants {
  private final SolrClient solrClient;
  private final FeatureSourceFactoryHelper featureSourceFactoryHelper;

  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static final int SOLR_BATCH_SIZE = 1000;

  /**
   * Constructor.
   *
   * @param solrClient the Solr client
   * @param featureSourceFactoryHelper the feature source factory helper
   */
  public SolrUtil(
      @NotNull SolrClient solrClient,
      @NotNull FeatureSourceFactoryHelper featureSourceFactoryHelper) {
    this.solrClient = solrClient;
    this.featureSourceFactoryHelper = featureSourceFactoryHelper;
  }

  public static String getIndexLayerId(String serviceId, String layerName) {
    return (serviceId + "__" + layerName).replaceAll(":", "__");
  }

  /**
   * Add or update a feature type index for a layer.
   *
   * @param indexLayerId the layer name, as created by using {@link #getIndexLayerId(String,
   *     String)}
   * @param tmFeatureType the feature type
   * @throws UnsupportedOperationException if the operation is not supported, possibly because not
   *     search field shave been defined
   * @throws IOException if an I/O error occurs
   * @throws SolrServerException if a Solr error occurs
   */
  public void addFeatureTypeIndexForLayer(String indexLayerId, TMFeatureType tmFeatureType)
      throws UnsupportedOperationException, IOException, SolrServerException {

    createSchemaIfNotExists();

    List<String> searchFields = tmFeatureType.getSettings().getSearchFields();
    List<String> displayFields = tmFeatureType.getSettings().getSearchDisplayFields();
    if (null == searchFields || searchFields.isEmpty()) {
      logger.info("No search fields configured for layer id: {}", indexLayerId);
      throw new UnsupportedOperationException(
          "No search fields configured for layer id: " + indexLayerId);
    }

    // add search and display properties to query
    Set<String> propertyNames = new HashSet<>();
    // always add primary key and default geometry to geotools query
    propertyNames.add(tmFeatureType.getPrimaryKeyAttribute());
    propertyNames.add(tmFeatureType.getDefaultGeometryAttribute());
    propertyNames.addAll(searchFields);

    final boolean hasDisplayFields = displayFields != null && !displayFields.isEmpty();
    if (hasDisplayFields) {
      propertyNames.addAll(displayFields);
    }

    clearIndexForLayer(indexLayerId);
    logger.info("Indexing started for layer id: {}", indexLayerId);
    // collect features to index
    SimpleFeatureSource fs = featureSourceFactoryHelper.openGeoToolsFeatureSource(tmFeatureType);
    Query q = new Query(fs.getName().toString());
    //    if (propertyNames.size() < 3) {
    //      // no search or display fields configured,
    //      // add all non-hidden string type properties to the query
    //      tmFeatureType
    //          .getAttributes()
    //          .forEach(
    //              a -> {
    //                if (TMAttributeTypeHelper.isGeometry(a.getType())
    //                    || a.getType() != TMAttributeType.STRING
    //                    || tmFeatureType.getSettings().getHideAttributes().contains(a.getName()))
    // {
    //                  return;
    //                }
    //                propertyNames.add(a.getName());
    //              });
    //    }
    // filter out any hidden properties (there should be none
    tmFeatureType.getSettings().getHideAttributes().forEach(propertyNames::remove);
    q.setPropertyNames(List.copyOf(propertyNames));
    q.setStartIndex(0);
    // TODO: make maxFeatures configurable? perhaps for WFS sources?
    // q.setMaxFeatures(10);
    logger.trace("Indexing query: {}", q);
    SimpleFeatureCollection simpleFeatureCollection = fs.getFeatures(q);
    List<FeatureDocument> docsBatch = new ArrayList<>(SOLR_BATCH_SIZE);
    // TODO this does not currently batch the feature source query
    UpdateResponse updateResponse;
    try (SimpleFeatureIterator iterator = simpleFeatureCollection.features()) {
      int indexCounter = 0;
      while (iterator.hasNext()) {
        indexCounter++;
        SimpleFeature feature = iterator.next();
        // note that this will create a unique document
        FeatureDocument doc = new FeatureDocument(feature.getID(), indexLayerId);
        // TODO these fields are added as a multivalued field, but should be single valued
        List<String> searchValues = new ArrayList<>();
        List<String> displayValues = new ArrayList<>();
        propertyNames.forEach(
            propertyName -> {
              Object value = feature.getAttribute(propertyName);
              if (value != null) {
                if (value instanceof Geometry) {
                  doc.setGeometry(((Geometry) value).toText());
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
              "Added {} documents to index, result status: {}",
              indexCounter,
              updateResponse.getStatus());
          docsBatch.clear();
        }
      }
    }
    if (!docsBatch.isEmpty()) {
      updateResponse = solrClient.addBeans(docsBatch);
      logger.info("Added last {} documents to index", docsBatch.size());
      logger.debug("Update response status: {}", updateResponse.getStatus());
    }

    updateResponse = this.solrClient.commit();
    logger.debug("Update response status: {}", updateResponse.getStatus());
  }

  /**
   * Clear the index for a layer.
   *
   * @param searchLayer the layer id, as created by using {@link #getIndexLayerId(String, String)}
   * @throws IOException if an I/O error occurs
   * @throws SolrServerException if a Solr error occurs
   */
  public void clearIndexForLayer(String searchLayer) throws IOException, SolrServerException {
    QueryResponse response =
        solrClient.query(new SolrQuery("exists(query(" + SEARCH_LAYER + ":" + searchLayer + "))"));
    if (response.getResults().getNumFound() > 0) {
      logger.info("Clearing index for searchLayer {}", searchLayer);
      UpdateResponse updateResponse = solrClient.deleteByQuery(LAYER_NAME_QUERY + searchLayer);
      logger.debug("Update response status: {}", updateResponse.getStatus());
      updateResponse = solrClient.commit();
      logger.debug("Update response status: {}", updateResponse.getStatus());
    } else {
      logger.info("No index to clear for layer {}", searchLayer);
    }
  }

  /**
   * Search in the index for a layer.
   *
   * @param layer the layer name, as created by using {@link #getIndexLayerId(String, String)}
   * @param q the query
   * @param start the start index
   * @param numResultsToReturn the number of results to return
   * @return the documents
   * @throws IOException if an I/O error occurs
   * @throws SolrServerException if a Solr error occurs
   */
  public SolrDocumentList findInIndex(String layer, String q, int start, int numResultsToReturn)
      throws IOException, SolrServerException {
    logger.info("Find in index for {}", layer);
    final SolrQuery query =
        new SolrQuery(INDEX_SEARCH_FIELD + ":" + q)
            .setShowDebugInfo(logger.isDebugEnabled())
            .addField(FID)
            .addField(SEARCH_LAYER)
            .addField(INDEX_DISPLAY_FIELD)
            .addField(INDEX_GEOM_FIELD)
            .addFilterQuery(LAYER_NAME_QUERY + layer)
            // cannot sort on multivalued field .setSort(FID, SolrQuery.ORDER.asc)
            .setRows(numResultsToReturn)
            .setStart(start * numResultsToReturn);
    query.set("q.op", "AND");
    logger.debug("Solr query: {}", query);

    final QueryResponse response = solrClient.query(query);
    logger.debug("response: {}", response);
    final SolrDocumentList documents = response.getResults();

    logger.debug("Found {} documents", documents.getNumFound());
    return documents;
  }

  /**
   * Programmatically create the schema if it does not exist. Only checks for the existence of the
   * search layer {@link Constants#SEARCH_LAYER}.
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
                "multiValued", false));
    schemaRequest.process(solrClient);

    logger.info("Creating Solr field type {}", INDEX_GEOM_FIELD);
    // TODO this should be a spatial field type using ("type", "location_rpt") but that requires
    // some more work
    SchemaRequest.AddField schemaRequestGeom =
        new SchemaRequest.AddField(
            Map.of(
                "name", INDEX_GEOM_FIELD,
                "type", "string",
                "indexed", true,
                "stored", true,
                "multiValued", false));
    schemaRequestGeom.process(solrClient);
  }

  /**
   * Close the Solr client.
   *
   * @throws IOException if an I/O error occurs
   */
  @Override
  public void close() throws IOException {
    this.solrClient.close();
  }
}
