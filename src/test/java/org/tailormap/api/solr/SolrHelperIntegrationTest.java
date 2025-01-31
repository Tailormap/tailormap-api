/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.solr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import org.apache.solr.client.solrj.SolrServerException;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.Issue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.persistence.SearchIndex;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.repository.FeatureTypeRepository;
import org.tailormap.api.repository.SearchIndexRepository;

@PostgresIntegrationTest
class SolrHelperIntegrationTest {
  @Autowired
  private SearchIndexRepository searchIndexRepository;

  @Autowired
  private FeatureSourceFactoryHelper featureSourceFactoryHelper;

  @Autowired
  private FeatureTypeRepository featureTypeRepository;

  @Autowired
  private SolrService solrService;

  @Test
  @Issue("https://b3partners.atlassian.net/browse/HTM-1428")
  @SuppressWarnings("PMD.UnitTestShouldIncludeAssert")
  @Transactional
  void indexWithoutPrimaryKey() throws Exception {
    searchIndexRepository
        .findByName("osm_no_pk")
        .ifPresentOrElse(
            searchIndex -> {
              TMFeatureType featureType =
                  featureTypeRepository.getReferenceById(searchIndex.getFeatureTypeId());
              try (SolrHelper solrHelper = new SolrHelper(this.solrService.getSolrClientForIndexing())) {
                searchIndex = solrHelper.addFeatureTypeIndex(
                    searchIndex, featureType, featureSourceFactoryHelper, searchIndexRepository);
                searchIndex = searchIndexRepository.save(searchIndex);
              } catch (IOException | SolrServerException e) {
                fail("Failed to add index", e);
              }
              assertEquals(
                  SearchIndex.Status.ERROR,
                  searchIndex.getStatus(),
                  "Expected index status to be ERROR");
              assertTrue(
                  searchIndex.getSummary().getErrorMessage().contains("primary key"),
                  "Expected error message to contain 'primary key'");
            },
            () -> fail("Index was not found"));
  }
}
