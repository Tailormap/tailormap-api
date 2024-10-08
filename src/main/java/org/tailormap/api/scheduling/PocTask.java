/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.PersistJobDataAfterExecution;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.quartz.QuartzJobBean;

/** POC task for testing purposes. This will only log messages and keep a counter. */
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class PocTask extends QuartzJobBean {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  protected void executeInternal(@NonNull JobExecutionContext context) {
    logger.info(
        "POC task {}:{} executing, details follow:",
        context.getJobDetail().getKey().getGroup(),
        context.getJobDetail().getKey().getName());

    // NOTE: This immutable map is a snapshot of the job data maps at the time of the job execution.
    JobDataMap mergedJobDataMap = context.getMergedJobDataMap();

    // NOTE: This map is mutable and can be used to store job data.
    JobDataMap jobDataMap = context.getJobDetail().getJobDataMap();

    int i = 1 + (int) mergedJobDataMap.getOrDefault("executions", 0);
    jobDataMap.put("executions", i);
    jobDataMap.put("lastExecutionFinished", Instant.now());
    context.setResult("POC task executed successfully");
    jobDataMap.put("status", Trigger.TriggerState.NORMAL);

    mergedJobDataMap.forEach((key, value) -> logger.info("   {}: {}", key, value));
  }
}
