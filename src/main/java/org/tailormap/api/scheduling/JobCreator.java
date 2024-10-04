/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.UUID;
import org.quartz.CronScheduleBuilder;
import org.quartz.DateBuilder;
import org.quartz.Job;
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
import org.springframework.stereotype.Service;

@Service
public class JobCreator {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final Scheduler scheduler;

  public JobCreator(Scheduler scheduler) {
    this.scheduler = scheduler;
  }

  /**
   * Create a job and schedule it with a cron expression.
   *
   * @param job the job to create
   * @param jobData a map with job data, the "type" key is mandatory
   * @param cronExpression the cron expression
   * @return the job name
   * @throws SchedulerException if the job could not be scheduled
   */
  public String createJob(Class<? extends Job> job, Map<?, ?> jobData, String cronExpression)
      throws SchedulerException {

    // Create a job
    JobDetail jobDetail =
        JobBuilder.newJob()
            .withIdentity(new JobKey(UUID.randomUUID().toString(), jobData.get("type").toString()))
            .withDescription(/* TODO add parameter of get from jobData */ job.getSimpleName())
            .usingJobData(new JobDataMap(jobData))
            .ofType(job)
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
