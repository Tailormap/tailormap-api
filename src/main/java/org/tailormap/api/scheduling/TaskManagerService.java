/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

import static io.sentry.quartz.SentryJobListener.SENTRY_SLUG_KEY;

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
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Service;

@Service
public class TaskManagerService {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final Scheduler scheduler;

  public TaskManagerService(@Autowired Scheduler scheduler) {
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
  public UUID createTask(
      Class<? extends QuartzJobBean> job, TMJobDataMap jobData, String cronExpression)
      throws SchedulerException {

    // Create a job
    JobDetail jobDetail =
        JobBuilder.newJob(job)
            .withIdentity(
                new JobKey(UUID.randomUUID().toString(), jobData.get(Task.TYPE_KEY).toString()))
            .withDescription(jobData.getDescription())
            .usingJobData(new JobDataMap(jobData))
            .build();

    // Create a trigger
    Trigger trigger =
        TriggerBuilder.newTrigger()
            .withIdentity(jobDetail.getKey().getName(), jobDetail.getKey().getGroup())
            .startAt(DateBuilder.futureDate(90, DateBuilder.IntervalUnit.SECOND))
            .withPriority(jobData.getPriority())
            .usingJobData(
                SENTRY_SLUG_KEY, "monitor_slug_cron_trigger_" + jobData.get(Task.TYPE_KEY))
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

    return UUID.fromString(jobDetail.getKey().getName());
  }

  /**
   * Reschedule a task using updated job data.
   *
   * @param jobKey the job key
   * @param newJobData the new job data
   * @throws SchedulerException if the job could not be rescheduled
   */
  public void updateTask(JobKey jobKey, TMJobDataMap newJobData) throws SchedulerException {

    if (scheduler.checkExists(jobKey)) {
      // there should only ever be one trigger for a job in TM
      Trigger oldTrigger = scheduler.getTriggersOfJob(jobKey).get(0);

      if (scheduler.checkExists(oldTrigger.getKey())) {

        JobDetail jobDetail = scheduler.getJobDetail(jobKey);
        JobDataMap jobDataMap = jobDetail.getJobDataMap();
        jobDataMap.putAll(newJobData);

        Trigger newTrigger =
            TriggerBuilder.newTrigger()
                .withIdentity(jobKey.getName(), jobKey.getGroup())
                .startAt(DateBuilder.futureDate(90, DateBuilder.IntervalUnit.SECOND))
                .withPriority(jobDataMap.getInt(Task.PRIORITY_KEY))
                .usingJobData(
                    SENTRY_SLUG_KEY,
                    "monitor_slug_cron_trigger_" + jobDataMap.getString(Task.TYPE_KEY))
                .withSchedule(
                    CronScheduleBuilder.cronSchedule(jobDataMap.getString(Task.CRON_EXPRESSION_KEY))
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();

        scheduler.rescheduleJob(oldTrigger.getKey(), newTrigger);
      }
    }
  }

  /**
   * Get the job key for a given type and uuid.
   *
   * @param jobType the type of the job
   * @param uuid the uuid of the job
   * @return the job key
   * @throws SchedulerException when the scheduler cannot be reached
   */
  @Nullable
  public JobKey getJobKey(String jobType, UUID uuid) throws SchedulerException {
    logger.debug("Finding job key for task {}:{}", jobType, uuid);
    return scheduler.getJobKeys(GroupMatcher.groupEquals(jobType)).stream()
        .filter(jobkey -> jobkey.getName().equals(uuid.toString()))
        .findFirst()
        .orElse(null);
  }
}
