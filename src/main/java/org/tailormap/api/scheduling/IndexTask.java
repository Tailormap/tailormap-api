/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.Instant;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrException;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.PersistJobDataAfterExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.persistence.SearchIndex;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.repository.FeatureTypeRepository;
import org.tailormap.api.repository.SearchIndexRepository;
import org.tailormap.api.solr.SolrHelper;
import org.tailormap.api.solr.SolrService;

@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class IndexTask extends QuartzJobBean implements Task {
  public static final String TYPE = "index";
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final FeatureSourceFactoryHelper featureSourceFactoryHelper;
  private final SolrService solrService;
  private final FeatureTypeRepository featureTypeRepository;
  private final SearchIndexRepository searchIndexRepository;

  private long index;

  public IndexTask(
      @Autowired SearchIndexRepository searchIndexRepository,
      @Autowired FeatureTypeRepository featureTypeRepository,
      @Autowired FeatureSourceFactoryHelper featureSourceFactoryHelper,
      @Autowired SolrService solrService) {

    this.featureSourceFactoryHelper = featureSourceFactoryHelper;
    this.solrService = solrService;
    this.featureTypeRepository = featureTypeRepository;
    this.searchIndexRepository = searchIndexRepository;
  }

  @Override
  protected void executeInternal(@NonNull JobExecutionContext context)
      throws JobExecutionException {

    final JobDataMap persistedJobData = context.getJobDetail().getJobDataMap();
    // final long searchIndexId = persistedJobData.getLong("index");
    logger.debug(
        "Start Executing IndexTask {} for index {}", context.getJobDetail().getKey(), getIndex());

    SearchIndex searchIndex =
        searchIndexRepository
            .findById(getIndex())
            .orElseThrow(() -> new JobExecutionException("Search index not found"));

    TMFeatureType indexingFT =
        featureTypeRepository
            .findById(searchIndex.getFeatureTypeId())
            .orElseThrow(() -> new JobExecutionException("Feature type not found"));

    try (SolrClient solrClient = solrService.getSolrClientForIndexing();
        SolrHelper solrHelper = new SolrHelper(solrClient)) {

      searchIndex.setStatus(SearchIndex.Status.INDEXING);
      searchIndex = searchIndexRepository.save(searchIndex);

      searchIndex =
          solrHelper.addFeatureTypeIndex(
              searchIndex, indexingFT, featureSourceFactoryHelper, searchIndexRepository);
      searchIndex = searchIndex.setStatus(SearchIndex.Status.INDEXED);
      searchIndexRepository.save(searchIndex);
      persistedJobData.put(
          "executions", (1 + (int) context.getMergedJobDataMap().getOrDefault("executions", 0)));
      persistedJobData.put("lastExecutionFinished", Instant.now());
      persistedJobData.put("lastResult", "Index task executed successfully");
      context.setResult("Index task executed successfully");
    } catch (UnsupportedOperationException | IOException | SolrServerException | SolrException e) {
      logger.error("Error indexing", e);
      searchIndex.setStatus(SearchIndex.Status.ERROR);
      searchIndexRepository.save(searchIndex);
      throw new JobExecutionException("Error indexing", e);
    }
  }

  @Override
  public String getType() {
    return TYPE;
  }

  public long getIndex() {
    return index;
  }

  public void setIndex(long index) {
    this.index = index;
  }
}
