/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

import io.micrometer.core.annotation.Timed;
import java.lang.invoke.MethodHandles;
import java.time.Instant;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.PersistJobDataAfterExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.tailormap.api.prometheus.PrometheusService;

@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class PrometheusPingTask extends QuartzJobBean implements Task {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final PrometheusService prometheusService;

  public PrometheusPingTask(PrometheusService prometheusService) {
    this.prometheusService = prometheusService;
  }

  @Timed(value = "prometheusServiceTask", description = "Time taken to ping prometheus")
  @Override
  protected void executeInternal(@NonNull JobExecutionContext context) throws JobExecutionException {
    final JobDataMap persistedJobData = context.getJobDetail().getJobDataMap();
    if (prometheusService.isPrometheusAvailable()) {
      persistedJobData.put(LAST_RESULT_KEY, "Prometheus is available. Check succeeded.");
      context.setResult("Prometheus is available");
    } else {
      logger.warn("PrometheusService is not available");
      persistedJobData.put(LAST_RESULT_KEY, "Prometheus is not available. Check failed.");
      context.setResult("Prometheus is not available");
    }
    persistedJobData.put(EXECUTION_FINISHED_KEY, Instant.now());
  }

  @Override
  public TaskType getType() {
    return TaskType.PROMETHEUS_PING;
  }

  @Override
  public String getDescription() {
    return "Ping Prometheus to ensure it is available.";
  }
}
