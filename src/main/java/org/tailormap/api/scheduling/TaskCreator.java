/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

import java.lang.invoke.MethodHandles;
import java.util.UUID;
import org.quartz.CronScheduleBuilder;
import org.quartz.DateBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Service;

@Service
public class TaskCreator {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final Scheduler scheduler;

  public TaskCreator(@Autowired Scheduler scheduler) {
    this.scheduler = scheduler;
  }

  /**
   * Create a job and schedule it with a cron expression.
   *
   * @param job the task class to create
   * @param jobData a map with job data, the {@code type} and {@code description} keys are mandatory
   * @param cronExpression the cron expression
   * @return the task name, a UUID
   * @throws SchedulerException if the job could not be scheduled
   */
  public String createTask(
      Class<? extends QuartzJobBean> job, TMJobDataMap jobData, String cronExpression)
      throws SchedulerException {

    // Create a job
    JobDetail jobDetail =
        JobBuilder.newJob(job)
            .withIdentity(new JobKey(UUID.randomUUID().toString(), jobData.get("type").toString()))
            .withDescription(jobData.getDescription())
            .usingJobData(new JobDataMap(jobData))
            .build();

    // Create a trigger
    Trigger trigger =
        TriggerBuilder.newTrigger()
            .withIdentity(jobDetail.getKey().getName(), jobDetail.getKey().getGroup())
            .startAt(DateBuilder.futureDate(30, DateBuilder.IntervalUnit.SECOND))
            .withSchedule(
                CronScheduleBuilder.cronSchedule(cronExpression)
                    .withMisfireHandlingInstructionFireAndProceed())
            .build();

    try {
      scheduler.scheduleJob(jobDetail, trigger);
    } catch (ObjectAlreadyExistsException ex) {
      logger.warn(
          "Job {} with trigger {} has not bean added to scheduler as it already exists.",
          jobDetail.getKey(),
          trigger.getKey());
      return null;
    }

    return jobDetail.getKey().getName();
  }
}
