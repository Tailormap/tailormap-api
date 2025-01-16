/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.InterruptableJob;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.PersistJobDataAfterExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.tailormap.api.admin.model.TaskProgressEvent;

/** POC task for testing purposes, this is a task that can be interrupted. */
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class InterruptablePocTask extends QuartzJobBean implements Task, InterruptableJob {

  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private boolean interrupted = false;

  private String description;

  @Override
  public void interrupt() {
    logger.info("Interrupting POC task");
    interrupted = true;
  }

  @Override
  protected void executeInternal(@NonNull JobExecutionContext context) throws JobExecutionException {

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

    TaskProgressEvent progressEvent = new TaskProgressEvent()
        .startedAt(OffsetDateTime.now(ZoneId.systemDefault()))
        .type(getType().getValue())
        .taskData(Map.of("jobKey", jobDetail.getKey().getName()))
        .uuid(UUID.fromString(jobDetail.getKey().getName()));

    try {
      for (int i = 0; i < 110; i += 10) {
        // Simulate some work for a random period of time
        long workingTime = (long) (Math.random() * 5000);
        logger.debug("Working for {} ms", workingTime);
        Thread.sleep(workingTime);
        logger.debug("Interruptable POC task is at {}%", i);
        context.setResult("Interruptable POC task is at %d%%".formatted(i));
        taskProgress(progressEvent.progress(i).total(100));
        if (interrupted) {
          logger.debug("Interruptable POC task interrupted at {}%", Instant.now());
          jobDataMap.put(
              Task.LAST_RESULT_KEY,
              "Interruptable POC task interrupted after %d%% iterations".formatted(i));
          jobDataMap.put(EXECUTION_FINISHED_KEY, null);
          context.setResult("Interruptable POC task interrupted after %d%% iterations".formatted(i));
          // bail out after interruption
          return;
        }
        // after 3rd iteration, interrupt the task
        if (i == 30) {
          interrupt();
        }
      }
    } catch (InterruptedException e) {
      logger.error("Thread interrupted", e);
    }

    jobDataMap.put(EXECUTION_COUNT_KEY, (1 + (int) mergedJobDataMap.getOrDefault(EXECUTION_COUNT_KEY, 0)));
    jobDataMap.put(EXECUTION_FINISHED_KEY, Instant.now());
    jobDataMap.put(Task.LAST_RESULT_KEY, "Interruptable POC task executed successfully");
    context.setResult("Interruptable POC task executed successfully");
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
