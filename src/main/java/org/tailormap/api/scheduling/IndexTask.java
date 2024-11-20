/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

import io.micrometer.core.annotation.Timed;
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
  public static final String INDEX_KEY = "indexId";
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final FeatureSourceFactoryHelper featureSourceFactoryHelper;
  private final SolrService solrService;
  private final FeatureTypeRepository featureTypeRepository;
  private final SearchIndexRepository searchIndexRepository;

  private long indexId;
  private String description;

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

  @Timed(value = "indexTask", description = "Time taken to execute index task")
  @Override
  protected void executeInternal(@NonNull JobExecutionContext context)
      throws JobExecutionException {

    final JobDataMap persistedJobData = context.getJobDetail().getJobDataMap();
    // final long indexId = persistedJobData.getLong(INDEX_KEY);
    logger.info(
        "Start Executing IndexTask {} for index {}, described with '{}'",
        context.getJobDetail().getKey(),
        getIndexId(),
        getDescription());

    SearchIndex searchIndex =
        searchIndexRepository
            .findById(getIndexId())
            .orElseThrow(() -> new JobExecutionException("Search index not found"));

    TMFeatureType indexingFT =
        featureTypeRepository
            .findById(searchIndex.getFeatureTypeId())
            .orElseThrow(() -> new JobExecutionException("Feature type not found"));

    try (SolrClient solrClient = solrService.getSolrClientForIndexing();
        SolrHelper solrHelper = new SolrHelper().withSolrClient(solrClient)) {

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
      persistedJobData.put(Task.LAST_RESULT_KEY, "Index task executed successfully");
      context.setResult("Index task executed successfully");
    } catch (UnsupportedOperationException | IOException | SolrServerException | SolrException e) {
      logger.error("Error indexing", e);
      searchIndex.setStatus(SearchIndex.Status.ERROR).setComment(e.getMessage());
      persistedJobData.put("lastExecutionFinished", null);
      persistedJobData.put(
          Task.LAST_RESULT_KEY,
          "Index task failed with " + e.getMessage() + ". Check logs for details");
      searchIndexRepository.save(searchIndex);
      context.setResult("Error indexing. Check logs for details.");
      throw new JobExecutionException("Error indexing", e);
    }
  }

  // <editor-fold desc="Getters and Setters">
  @Override
  public TaskType getType() {
    return TaskType.INDEX;
  }

  public long getIndexId() {
    return indexId;
  }

  public void setIndexId(long indexId) {
    this.indexId = indexId;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public void setDescription(String description) {
    this.description = description;
  }
  // </editor-fold>
}
