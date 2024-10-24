/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository.events;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.core.annotation.HandleAfterDelete;
import org.springframework.data.rest.core.annotation.HandleBeforeSave;
import org.springframework.data.rest.core.annotation.RepositoryEventHandler;
import org.springframework.stereotype.Component;
import org.tailormap.api.persistence.SearchIndex;
import org.tailormap.api.scheduling.IndexTask;
import org.tailormap.api.scheduling.TMJobDataMap;
import org.tailormap.api.scheduling.Task;
import org.tailormap.api.scheduling.TaskManagerService;
import org.tailormap.api.scheduling.TaskType;

/**
 * Event handler for Solr indexes; when a {@code SearchIndex} is created, updated or deleted a
 * {@code Task} is associated.
 */
@Component
@RepositoryEventHandler
public class SearchIndexEventHandler {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final Scheduler scheduler;
  private final TaskManagerService taskManagerService;

  public SearchIndexEventHandler(
      @Autowired Scheduler scheduler, @Autowired TaskManagerService taskManagerService) {
    this.scheduler = scheduler;
    this.taskManagerService = taskManagerService;
  }

  /**
   * Handle after delete. Delete any associated task.
   *
   * @param searchIndex the search index that was deleted
   */
  @HandleAfterDelete
  public void afterDeleteSearchIndexEventHandler(SearchIndex searchIndex)
      throws SchedulerException {
    if (null != searchIndex.getSchedule()) {
      JobKey jobKey =
          taskManagerService.getJobKey(TaskType.INDEX, searchIndex.getSchedule().getUuid());

      if (null != jobKey && scheduler.checkExists(jobKey)) {
        logger.info(
            "Deleting index task {} associated with search index: {}",
            searchIndex.getSchedule().getUuid(),
            searchIndex.getName());
        boolean succes = scheduler.deleteJob(jobKey);
        logger.info(
            "Task {}:{} deletion {}",
            jobKey.getGroup(),
            jobKey.getName(),
            (succes ? "succeeded" : "failed"));
      }
    }
  }

  /**
   * Handle before save. Create or update the associated task.
   *
   * @param searchIndex the search index that was saved
   * @throws SchedulerException if the task could not be created or updated
   */
  @HandleBeforeSave
  public void beforeSaveSearchIndexEventHandler(SearchIndex searchIndex) throws SchedulerException {
    // TODO we don't handle the case where the schedule is null here; we would need to determine if
    //   a task exists that was associated with the search index before;
    //   this case can already be handled requesting a delete of the scheduled task instead
    if (null != searchIndex.getSchedule()) {
      if (null == searchIndex.getSchedule().getUuid()) {
        validateNoTaskExistsForIndex(searchIndex);
        // no task exists yet, create one
        logger.info("Creating new task associated with search index: {}", searchIndex.getName());
        TMJobDataMap jobDataMap =
            new TMJobDataMap(
                Map.of(
                    Task.TYPE_KEY,
                    TaskType.INDEX,
                    Task.DESCRIPTION_KEY,
                    searchIndex.getSchedule().getDescription(),
                    IndexTask.INDEX_KEY,
                    searchIndex.getId().toString()));
        jobDataMap.setPriority(searchIndex.getSchedule().getPriority());
        final UUID uuid =
            taskManagerService.createTask(
                IndexTask.class, jobDataMap, searchIndex.getSchedule().getCronExpression());
        searchIndex.getSchedule().setUuid(uuid);
      } else {
        // UUID given, task should exist; update it
        logger.info(
            "Updating task {} associated with search index: {}",
            searchIndex.getSchedule().getUuid(),
            searchIndex.getName());

        JobKey jobKey =
            taskManagerService.getJobKey(TaskType.INDEX, searchIndex.getSchedule().getUuid());
        if (null != jobKey && scheduler.checkExists(jobKey)) {
          // the only things that may have changed are the cron expression, priority and description
          JobDataMap jobDataMap = scheduler.getJobDetail(jobKey).getJobDataMap();
          jobDataMap.put(Task.DESCRIPTION_KEY, searchIndex.getSchedule().getDescription());
          jobDataMap.put(Task.CRON_EXPRESSION_KEY, searchIndex.getSchedule().getCronExpression());
          jobDataMap.put(Task.PRIORITY_KEY, searchIndex.getSchedule().getPriority());

          taskManagerService.updateTask(jobKey, new TMJobDataMap(jobDataMap));
        }
      }
    }
  }

  /**
   * Validate that no scheduled task exists for the given index.
   *
   * @param searchIndex the search index to validate for scheduling a task
   * @throws SchedulerException if there is a task that is already associated with the given index
   */
  private void validateNoTaskExistsForIndex(SearchIndex searchIndex) throws SchedulerException {
    Optional<JobDataMap> jobDataMapOptional =
        scheduler.getJobKeys(GroupMatcher.groupEquals(TaskType.INDEX.getValue())).stream()
            .map(
                jobKey -> {
                  try {
                    return scheduler.getJobDetail(jobKey).getJobDataMap();
                  } catch (SchedulerException e) {
                    logger.error("Error getting task detail", e);
                    return null;
                  }
                })
            .filter(Objects::nonNull)
            .filter(
                jobDataMap ->
                    searchIndex.getId().equals(jobDataMap.getLongValue(IndexTask.INDEX_KEY)))
            .findFirst();

    if (jobDataMapOptional.isPresent()) {
      logger.warn("A scheduled task already exists for search index: {}", searchIndex.getName());
      throw new SchedulerException(
          "A scheduled task already exists for search index: '%s'"
              .formatted(searchIndex.getName()));
    }
  }
}
