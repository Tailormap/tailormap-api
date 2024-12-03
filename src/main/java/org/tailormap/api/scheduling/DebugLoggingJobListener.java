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
public class DebugLoggingJobListener implements JobListener {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public String getName() {
    return this.getClass().getSimpleName();
  }

  @Override
  public void jobToBeExecuted(JobExecutionContext context) {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Job {}:{} about to be executed.",
          context.getJobDetail().getKey().getGroup(),
          context.getJobDetail().getKey().getName());
      logger.debug(
          "Job data map before execution: {}", context.getMergedJobDataMap().getWrappedMap());
    }
  }

  @Override
  public void jobExecutionVetoed(JobExecutionContext context) {
    if (logger.isDebugEnabled())
      logger.debug(
          "Job {}:{} execution vetoed.",
          context.getJobDetail().getKey().getGroup(),
          context.getJobDetail().getKey().getName());
  }

  @Override
  public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
    if (logger.isDebugEnabled())
      logger.debug(
          "Job {}:{} was executed in: {} ms. With result: {}",
          context.getJobDetail().getKey().getGroup(),
          context.getJobDetail().getKey().getName(),
          context.getJobRunTime(),
          context.getResult());

    if (null != jobException) {
      if (logger.isDebugEnabled())
        logger.error(
            "Job {}:{} threw an exception: {}",
            context.getJobDetail().getKey().getGroup(),
            context.getJobDetail().getKey().getName(),
            jobException.getMessage());
    }
  }
}
