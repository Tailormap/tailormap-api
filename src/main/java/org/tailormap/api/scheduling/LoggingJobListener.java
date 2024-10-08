/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

import java.lang.invoke.MethodHandles;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingJobListener implements JobListener {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public String getName() {
    return this.getClass().getSimpleName();
  }

  @Override
  public void jobToBeExecuted(JobExecutionContext context) {
    logger.info(
        "Job {}:{} about to be executed.",
        context.getJobDetail().getKey().getGroup(),
        context.getJobDetail().getKey().getName());
  }

  @Override
  public void jobExecutionVetoed(JobExecutionContext context) {
    logger.warn(
        "Job {}:{} execution vetoed.",
        context.getJobDetail().getKey().getGroup(),
        context.getJobDetail().getKey().getName());
  }

  @Override
  public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
    logger.info(
        "Job {}:{} was executed in: {} ms.",
        context.getJobDetail().getKey().getGroup(),
        context.getJobDetail().getKey().getName(),
        context.getJobRunTime());

    if (null != jobException) {
      logger.error(
          "Job {}:{} threw an exception: {}",
          context.getJobDetail().getKey().getGroup(),
          context.getJobDetail().getKey().getName(),
          jobException.getMessage());
    }

    context.getJobDetail().getJobDataMap().put("runtime", context.getJobRunTime());
    logger.info("Job data map after execution:");
    context.getMergedJobDataMap().forEach((key, value) -> logger.info("   {}: {}", key, value));
  }
}
