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
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.PersistJobDataAfterExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.quartz.QuartzJobBean;

/** POC task for testing purposes. */
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class PocTask extends QuartzJobBean {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private String foo;

  @Override
  protected void executeInternal(@NonNull JobExecutionContext context) {
    final JobDetail jobDetail = context.getJobDetail();

    // NOTE: This immutable map is a snapshot of the job data maps at the time of the job execution.
    final JobDataMap mergedJobDataMap = context.getMergedJobDataMap();

    // NOTE: This map is mutable and can be used to store job data.
    final JobDataMap jobDataMap = jobDetail.getJobDataMap();

    // this.foo is set through QuartzJobBean
    logger.debug("foo: {}", getFoo());

    logger.debug(
        "executing POC task {}:{}, details: {}",
        jobDetail.getKey().getGroup(),
        jobDetail.getKey().getName(),
        mergedJobDataMap.getWrappedMap());

    try {
      for (int i = 0; i < 110; i += 10) {
        // Simulate some work for a random period of time
        long workingTime = (long) (Math.random() * 5000);
        logger.debug("Working for {} ms", workingTime);
        Thread.sleep(workingTime);
        logger.debug("POC task is at {}%", i);
        context.setResult(String.format("POC task is at %d%%", i));
      }
    } catch (InterruptedException e) {
      logger.error("Thread interrupted", e);
    }

    jobDataMap.put("executions", (1 + (int) mergedJobDataMap.getOrDefault("executions", 0)));
    jobDataMap.put("lastExecutionFinished", Instant.now());
    jobDataMap.put("lastResult", "POC task executed successfully");
    context.setResult("POC task executed successfully");
  }

  public String getFoo() {
    return foo;
  }

  public void setFoo(String foo) {
    this.foo = foo;
  }
}
