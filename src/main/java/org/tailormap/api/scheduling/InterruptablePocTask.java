/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.InterruptableJob;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.PersistJobDataAfterExecution;
import org.quartz.UnableToInterruptJobException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.quartz.QuartzJobBean;

/** POC task for testing purposes, this is a task that can be interrupted. */
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class InterruptablePocTask extends QuartzJobBean implements Task, InterruptableJob {

  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private boolean interrupted = false;

  private String description;

  @Override
  public void interrupt() throws UnableToInterruptJobException {
    logger.info("Interrupting POC task");
    interrupted = true;
  }

  @Override
  protected void executeInternal(@NonNull JobExecutionContext context)
      throws JobExecutionException {

    final JobDetail jobDetail = context.getJobDetail();

    // NOTE: This immutable map is a snapshot of the job data maps at the time of the job execution.
    final JobDataMap mergedJobDataMap = context.getMergedJobDataMap();

    // NOTE: This map is mutable and can be used to store job data.
    final JobDataMap jobDataMap = jobDetail.getJobDataMap();

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
        logger.debug("Interruptable POC task is at {}%", i);
        context.setResult("Interruptable POC task is at %d%%".formatted(i));

        if (interrupted) {
          logger.debug("Interruptable POC task interrupted at {}%", Instant.now());
          jobDataMap.put(
              Task.LAST_RESULT_KEY,
              "Interruptable POC task interrupted after %d%% iterations".formatted(i));
          jobDataMap.put("lastExecutionFinished", null);
          context.setResult(
              "Interruptable POC task interrupted after %d%% iterations".formatted(i));
          // bail out after interruption
          return;
        }

        int executions = (1 + (int) mergedJobDataMap.getOrDefault("executions", 0));
        jobDataMap.put("executions", executions);
        jobDataMap.put("lastExecutionFinished", Instant.now());
        jobDataMap.put(Task.LAST_RESULT_KEY, "Interruptable POC task executed successfully");
        context.setResult("Interruptable POC task executed successfully");
      }
    } catch (InterruptedException e) {
      logger.error("Thread interrupted", e);
    }
  }

  @Override
  public TaskType getType() {
    return TaskType.INTERRUPTABLEPOC;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public void setDescription(String description) {
    this.description = description;
  }
}
