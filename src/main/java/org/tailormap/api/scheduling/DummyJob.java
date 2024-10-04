/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

import java.lang.invoke.MethodHandles;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.quartz.QuartzJobBean;

/** Dummy job for testing purposes. This will only log messages. */
@DisallowConcurrentExecution
public class DummyJob extends QuartzJobBean {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  protected void executeInternal(@NonNull JobExecutionContext context) {
    logger.info("Dummy job executing, details follow:");
    context
        .getJobDetail()
        .getJobDataMap()
        .forEach((key, value) -> logger.info("   Key: {}, Value: {}", key, value));
  }
}
