/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository.events;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.rest.core.annotation.HandleAfterDelete;
import org.springframework.data.rest.core.annotation.HandleBeforeSave;
import org.springframework.data.rest.core.annotation.RepositoryEventHandler;
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.persistence.SearchIndex;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.json.AppLayerSettings;
import org.tailormap.api.repository.ApplicationRepository;
import org.tailormap.api.repository.SearchIndexRepository;
import org.tailormap.api.solr.SolrHelper;
import org.tailormap.api.solr.SolrService;

/** Event handler for Solr indexes when a {@code TMFeatureType} is updated or deleted. */
@RepositoryEventHandler
public class SolrTMFeatureTypeEventHandler {

  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final SearchIndexRepository searchIndexRepository;
  private final SolrService solrService;
  private final FeatureSourceFactoryHelper featureSourceFactoryHelper;
  private final ApplicationRepository applicationRepository;

  public SolrTMFeatureTypeEventHandler(
      SearchIndexRepository searchIndexRepository,
      SolrService solrService,
      FeatureSourceFactoryHelper featureSourceFactoryHelper,
      ApplicationRepository applicationRepository) {
    this.searchIndexRepository = searchIndexRepository;
    this.solrService = solrService;
    this.featureSourceFactoryHelper = featureSourceFactoryHelper;
    this.applicationRepository = applicationRepository;
  }

  /**
   * Handle the update of a TMFeatureType.
   *
   * @param tmFeatureType the TMFeatureType to handle
   */
  @HandleBeforeSave
  public void handleTMFeatureTypeUpdate(TMFeatureType tmFeatureType) {
    logger.debug("Handling TMFeatureType save event for: {}", tmFeatureType);
    // determine if it is a new FT or an update
    if (null == tmFeatureType.getId()) {
      // do nothing as there is no index defined for a new feature type
      logger.debug("New TMFeatureType: {}", tmFeatureType);
    } else {
      logger.debug("Updated TMFeatureType: {}", tmFeatureType);
      searchIndexRepository.findByFeatureTypeId(tmFeatureType.getId()).stream()
          .findAny()
          .ifPresent(
              searchIndex -> {
                logger.debug(
                    "Updating search index {} for feature type: {}",
                    searchIndex.getName(),
                    searchIndex);

                try (SolrHelper solrHelper =
                    new SolrHelper(solrService.getSolrClientForIndexing())) {
                  solrHelper.addFeatureTypeIndex(
                      searchIndex,
                      tmFeatureType,
                      featureSourceFactoryHelper,
                      searchIndexRepository);
                } catch (UnsupportedOperationException
                    | IOException
                    | SolrServerException
                    | SolrException e) {
                  logger.error("Error re-indexing", e);
                  searchIndex.setStatus(SearchIndex.Status.ERROR);
                  searchIndexRepository.save(searchIndex);
                }
              });
    }
  }

  /**
   * Handle the deletion of a TMFeatureType.
   *
   * @param tmFeatureType the TMFeatureType to handle
   */
  @HandleAfterDelete
  public void handleTMFeatureTypeDeleteForSolr(TMFeatureType tmFeatureType) {
    logger.debug("Handling TMFeatureType delete event for: {}", tmFeatureType);
    searchIndexRepository.findByFeatureTypeId(tmFeatureType.getId()).stream()
        .findAny()
        .ifPresent(
            searchIndex -> {
              logger.info(
                  "Deleting search index {} for feature type: {}",
                  searchIndex.getName(),
                  searchIndex);

              try (SolrHelper solrHelper = new SolrHelper(solrService.getSolrClientForIndexing())) {
                solrHelper.clearIndexForLayer(searchIndex.getId());
                searchIndexRepository.delete(searchIndex);
                // find any application layers that use this index clear the index from them
                applicationRepository
                    .findByIndexId(searchIndex.getId())
                    .forEach(
                        application -> {
                          application
                              .getAllAppTreeLayerNode()
                              .forEach(
                                  appTreeLayerNode -> {
                                    AppLayerSettings appLayerSettings =
                                        application.getAppLayerSettings(appTreeLayerNode);
                                    if (null != appLayerSettings.getSearchIndexId()
                                        && appLayerSettings
                                            .getSearchIndexId()
                                            .equals(searchIndex.getId())) {
                                      appLayerSettings.setSearchIndexId(null);
                                    }
                                  });
                          applicationRepository.save(application);
                        });
              } catch (UnsupportedOperationException
                  | IOException
                  | SolrServerException
                  | SolrException e) {
                logger.error("Error deleting index for {}", searchIndex, e);
              }
            });
  }
}
