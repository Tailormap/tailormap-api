/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.solr;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import nl.b3p.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.persistence.helper.TMAttributeTypeHelper;
import nl.b3p.tailormap.api.persistence.json.TMAttributeType;
import nl.b3p.tailormap.api.util.Constants;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Solr utility class. This class provides methods to add or update a feature type index for a
 * layer, find in the index for a layer, and clear the index for a layer. It also provides a method
 * to close the Solr client as well as automatically closing the client when used in a
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

  public static String getLayerId(String serviceId, String layerName) {
    return (serviceId + "__" + layerName).replaceAll(":", "__");
  }

  /**
   * Add or update a feature type index for a layer.
   *
   * @param layer the layer name, as created by using {@link #getLayerId(String, String)}
   * @param tmFeatureType the feature type
   * @throws UnsupportedOperationException if the operation is not supported
   * @throws IOException if an I/O error occurs
   * @throws SolrServerException if a Solr error occurs
   */
  public void addFeatureTypeIndexForLayer(String layer, TMFeatureType tmFeatureType)
      throws UnsupportedOperationException, IOException, SolrServerException {
    UpdateResponse updateResponse;

    clearIndexForLayer(layer);

    logger.info("Indexing started");
    // collect features to index
    SimpleFeatureSource fs = featureSourceFactoryHelper.openGeoToolsFeatureSource(tmFeatureType);
    Query q = new Query(fs.getName().toString());
    Set<String> propertyNames = new HashSet<>();
    // always add primary key and default geometry
    propertyNames.add(tmFeatureType.getPrimaryKeyAttribute());
    propertyNames.add(tmFeatureType.getDefaultGeometryAttribute());
    // add search and display properties to query
    List<String> searchFields = tmFeatureType.getSettings().getSearchFields();
    List<String> displayFields = tmFeatureType.getSettings().getSearchDisplayFields();
    final boolean hasSearchFields = searchFields != null && !searchFields.isEmpty();

    if (hasSearchFields) {
      propertyNames.addAll(searchFields);
    }
    final boolean hasDisplayFields = displayFields != null && !displayFields.isEmpty();
    if (hasDisplayFields) {
      propertyNames.addAll(displayFields);
    }

    if (propertyNames.size() < 3) {
      // no search or display fields configured, add all non-hidden string type properties to the
      // query
      tmFeatureType
          .getAttributes()
          .forEach(
              a -> {
                if (TMAttributeTypeHelper.isGeometry(a.getType())
                    || a.getType() != TMAttributeType.STRING
                    || tmFeatureType.getSettings().getHideAttributes().contains(a.getName())) {
                  return;
                }
                propertyNames.add(a.getName());
              });
    }
    q.setPropertyNames(List.copyOf(propertyNames));
    q.setStartIndex(0);
    // TODO: make maxFeatures configurable?
    // q.setMaxFeatures(10);
    logger.trace("Indexing query: {}", q);
    SimpleFeatureCollection simpleFeatureCollection = fs.getFeatures(q);

    List<SolrInputDocument> docsBatch = new ArrayList<>(SOLR_BATCH_SIZE);
    try (SimpleFeatureIterator iterator = simpleFeatureCollection.features()) {
      int indexCounter = 0;
      while (iterator.hasNext()) {
        indexCounter++;
        SimpleFeature feature = iterator.next();
        SolrInputDocument doc = new SolrInputDocument();
        // TODO these fields are added as a multivalued field, but should be single valued
        doc.setField(FID, feature.getID());
        doc.setField(LAYER_NAME, layer);

        propertyNames.forEach(
            propertyName -> {
              Object value = feature.getAttribute(propertyName);
              if (value != null) {
                if (value instanceof Geometry) {
                  // TODO this field is added as a multivalued field, but should be single valued
                  doc.setField(INDEX_GEOM_FIELD, ((Geometry) value).toText());
                } else {
                  // when display and/or search fields are configured, add the value to the search
                  // and/or display field otherwise add the value to the search and display field
                  if (hasSearchFields) {
                    if (searchFields.contains(propertyName)) {
                      doc.addField(INDEX_SEARCH_FIELD, value);
                    }
                  } else {
                    doc.addField(INDEX_SEARCH_FIELD, value);
                  }
                  if (hasDisplayFields) {
                    if (displayFields.contains(propertyName)) {
                      doc.addField(INDEX_DISPLAY_FIELD, value);
                    }
                  } else {
                    doc.addField(INDEX_DISPLAY_FIELD, value);
                  }
                }
              }
            });
        docsBatch.add(doc);
        if (indexCounter % SOLR_BATCH_SIZE == 0) {
          updateResponse = solrClient.add(docsBatch);
          logger.info(
              "Added {} documents to index, result status: {}",
              indexCounter,
              updateResponse.getStatus());
          docsBatch.clear();
        }
      }
    }
    if (!docsBatch.isEmpty()) {
      updateResponse = solrClient.add(docsBatch);
      logger.info("Added last {} documents to index", docsBatch.size());
      logger.debug("Update response status: {}", updateResponse.getStatus());
    }

    updateResponse = this.solrClient.commit();
    logger.debug("Update response status: {}", updateResponse.getStatus());
  }

  /**
   * Search in the index for a layer.
   *
   * @param layer the layer name, as created by using {@link #getLayerId(String, String)}
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
            .addField(LAYER_NAME)
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
   * Clear the index for a layer.
   *
   * @param layer the layer id, as created by using {@link #getLayerId(String, String)}
   * @throws IOException if an I/O error occurs
   * @throws SolrServerException if a Solr error occurs
   */
  public void clearIndexForLayer(String layer) throws IOException, SolrServerException {
    QueryResponse response = solrClient.query(new SolrQuery("exists(" + LAYER_NAME + ")"));
    if (response.getResults().getNumFound() > 0) {
      logger.info("Clearing index for {}", layer);
      UpdateResponse updateResponse = solrClient.deleteByQuery(LAYER_NAME_QUERY + layer);
      logger.debug("Update response status: {}", updateResponse.getStatus());
      updateResponse = solrClient.commit();
      logger.debug("Update response status: {}", updateResponse.getStatus());
    }
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
