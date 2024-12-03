/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

import java.lang.invoke.MethodHandles;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.PersistJobDataAfterExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;

/** POC task for testing purposes. This task always fails. */
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class FailingPocTask extends QuartzJobBean implements Task {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private String description;

  @Override
  protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
    final JobDetail jobDetail = context.getJobDetail();

    // NOTE: This map is mutable and can be used to store job data.
    final JobDataMap jobDataMap = jobDetail.getJobDataMap();

    try {
      // Simulate some work for a random period of time
      long workingTime = (long) (Math.random() * 10000);
      logger.debug("Working for {} ms", workingTime);
      Thread.sleep(workingTime);
      throw new UnsupportedOperationException("Failing POC task failed.");
    } catch (Exception e) {
      logger.error("Failing POC task failed.", e);
      jobDataMap.put(Task.LAST_RESULT_KEY, "POC task executed unsuccessfully");
      context.setResult("POC task executed unsuccessfully");
      throw new JobExecutionException(e);
    }
  }

  // <editor-fold desc="Getters and Setters">

  @Override
  public TaskType getType() {
    return TaskType.FAILINGPOC;
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
