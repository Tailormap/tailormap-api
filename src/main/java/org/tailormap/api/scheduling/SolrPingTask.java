/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.PersistJobDataAfterExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.tailormap.api.solr.SolrService;

@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class SolrPingTask extends QuartzJobBean implements Task {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final SolrService solrService;

  public SolrPingTask(SolrService solrService) {
    this.solrService = solrService;
  }

  @Override
  protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
    final JobDataMap persistedJobData = context.getJobDetail().getJobDataMap();
    if (solrService.isSolrServiceAvailable()) {
      persistedJobData.put(LAST_RESULT_KEY, "Solr is available. Check succeeded.");
      context.setResult("Solr is available");
    } else {
      logger.warn("Solr service is not available");
      persistedJobData.put(LAST_RESULT_KEY, "Solr is not available. Check failed.");
      context.setResult("Solr is not available");
    }
    persistedJobData.put(EXECUTION_FINISHED_KEY, Instant.now());
  }

  @Override
  public TaskType getType() {
    return TaskType.SOLR_PING;
  }

  @Override
  public String getDescription() {
    return "Ping Solr to ensure it is available.";
  }
}
