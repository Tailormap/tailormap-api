/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

import static ch.rasc.sse.eventbus.SseEvent.DEFAULT_EVENT;
import static org.tailormap.api.admin.model.ServerSentEvent.EventTypeEnum.TASK_PROGRESS;

import ch.rasc.sse.eventbus.SseEvent;
import ch.rasc.sse.eventbus.SseEventBus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.UUID;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.tailormap.api.admin.model.SearchIndexSummary;
import org.tailormap.api.admin.model.ServerSentEvent;
import org.tailormap.api.admin.model.TaskProgressEvent;
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
  private final FeatureTypeRepository featureTypeRepository;
  private final SearchIndexRepository searchIndexRepository;
  private final SolrService solrService;
  private final SseEventBus eventBus;
  private final ObjectMapper objectMapper;

  @Value("${tailormap-api.solr-batch-size:1000}")
  private int solrBatchSize;

  @Value("${tailormap-api.solr-geometry-validation-rule:repairBuffer0}")
  private String solrGeometryValidationRule;

  private long indexId;
  private String description;

  public IndexTask(
      @Autowired SearchIndexRepository searchIndexRepository,
      @Autowired FeatureTypeRepository featureTypeRepository,
      @Autowired FeatureSourceFactoryHelper featureSourceFactoryHelper,
      @Autowired SolrService solrService,
      @Autowired SseEventBus eventBus,
      @Autowired ObjectMapper objectMapper) {

    this.featureSourceFactoryHelper = featureSourceFactoryHelper;
    this.solrService = solrService;
    this.featureTypeRepository = featureTypeRepository;
    this.searchIndexRepository = searchIndexRepository;
    this.eventBus = eventBus;
    this.objectMapper = objectMapper;
  }

  @Timed(value = "indexTask", description = "Time taken to execute index task")
  @Counted(value = "indexTaskCount", description = "Number of times index task executed")
  @Override
  protected void executeInternal(@NonNull JobExecutionContext context) throws JobExecutionException {

    final JobDataMap persistedJobData = context.getJobDetail().getJobDataMap();
    logger.info(
        "Start Executing IndexTask {} for index {}, described with '{}'",
        context.getJobDetail().getKey(),
        getIndexId(),
        getDescription());

    SearchIndex searchIndex = searchIndexRepository
        .findById(getIndexId())
        .orElseThrow(() -> new JobExecutionException("Search index not found"));

    TMFeatureType indexingFT = featureTypeRepository
        .findById(searchIndex.getFeatureTypeId())
        .orElseThrow(() -> new JobExecutionException("Feature type for indexing not found"));

    try (SolrClient solrClient = solrService.getSolrClientForIndexing();
        SolrHelper solrHelper = new SolrHelper(solrClient)
            .withBatchSize(solrBatchSize)
            .withGeometryValidationRule(solrGeometryValidationRule)) {

      persistedJobData.put(EXECUTION_FINISHED_KEY, null);
      persistedJobData.put(LAST_RESULT_KEY, null);
      searchIndex = searchIndexRepository.save(searchIndex.setStatus(SearchIndex.Status.INDEXING));

      searchIndex = solrHelper.addFeatureTypeIndex(
          searchIndex,
          indexingFT,
          featureSourceFactoryHelper,
          searchIndexRepository,
          this::taskProgress,
          UUID.fromString(context.getTrigger().getJobKey().getName()));
      searchIndex = searchIndexRepository.save(searchIndex.setStatus(SearchIndex.Status.INDEXED));
      persistedJobData.put(
          EXECUTION_COUNT_KEY,
          (1 + (int) context.getMergedJobDataMap().getOrDefault(EXECUTION_COUNT_KEY, 0)));
      persistedJobData.put(EXECUTION_FINISHED_KEY, Instant.now());
      persistedJobData.put(LAST_RESULT_KEY, "Index task executed successfully");
      context.setResult("Index task executed successfully");
    } catch (UnsupportedOperationException | IOException | SolrServerException | SolrException e) {
      logger.error("Error indexing", e);
      persistedJobData.put(EXECUTION_FINISHED_KEY, null);
      persistedJobData.put(
          LAST_RESULT_KEY, "Index task failed with " + e.getMessage() + ". Check logs for details");
      searchIndexRepository.save(searchIndex
          .setStatus(SearchIndex.Status.ERROR)
          .setSummary(new SearchIndexSummary().errorMessage(e.getMessage())));
      context.setResult("Error indexing. Check logs for details.");
      throw new JobExecutionException("Error indexing", e);
    }
  }

  @Override
  public void taskProgress(TaskProgressEvent event) {
    ServerSentEvent serverSentEvent =
        new ServerSentEvent().eventType(TASK_PROGRESS).details(event);
    try {
      eventBus.handleEvent(SseEvent.of(DEFAULT_EVENT, objectMapper.writeValueAsString(serverSentEvent)));
    } catch (JsonProcessingException e) {
      logger.error("Error publishing indexing task progress event", e);
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
