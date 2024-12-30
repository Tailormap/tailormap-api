/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

import static ch.rasc.sse.eventbus.SseEvent.DEFAULT_EVENT;
import static org.tailormap.api.admin.model.ServerSentEvent.EventTypeEnum.TASK_PROGRESS;

import ch.rasc.sse.eventbus.SseEvent;
import ch.rasc.sse.eventbus.SseEventBus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.PersistJobDataAfterExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.tailormap.api.admin.model.ServerSentEvent;
import org.tailormap.api.admin.model.TaskProgressEvent;

/** POC task for testing purposes. */
@DisallowConcurrentExecution
@PersistJobDataAfterExecution
public class PocTask extends QuartzJobBean implements Task {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private String foo;
  private String description;
  private final SseEventBus eventBus;
  private final ObjectMapper objectMapper;

  public PocTask(SseEventBus eventBus, ObjectMapper objectMapper) {
    this.eventBus = eventBus;
    this.objectMapper = objectMapper;
  }

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
      TaskProgressEvent progressEvent = new TaskProgressEvent()
          .startedAt(OffsetDateTime.now(ZoneId.systemDefault()))
          .type(getType().getValue())
          .uuid(UUID.fromString(jobDetail.getKey().getName()));

      for (int i = 0; i < 110; i += 10) {
        // Simulate some work for a random period of time
        long workingTime = (long) (Math.random() * 5000);
        logger.debug("Working for {} ms", workingTime);
        Thread.sleep(workingTime);
        logger.debug("POC task is at {}%", i);
        context.setResult("POC task is at %d%%".formatted(i));
        taskProgress(progressEvent.progress(i).total(100));
      }
    } catch (InterruptedException e) {
      logger.error("Thread interrupted", e);
    }

    int executions = (1 + (int) mergedJobDataMap.getOrDefault("executions", 0));
    jobDataMap.put("executions", executions);
    jobDataMap.put("lastExecutionFinished", Instant.now());
    jobDataMap.put(Task.LAST_RESULT_KEY, "POC task executed successfully");
    context.setResult("POC task executed successfully");

    setFoo("foo executed: " + executions);
  }

  @Override
  public void taskProgress(TaskProgressEvent event) {
    ServerSentEvent serverSentEvent =
        new ServerSentEvent().eventType(TASK_PROGRESS).details(event);
    try {
      eventBus.handleEvent(SseEvent.of(DEFAULT_EVENT, objectMapper.writeValueAsString(serverSentEvent)));
    } catch (JsonProcessingException e) {
      logger.error("Error publishing poc task progress event", e);
    }
  }

  // <editor-fold desc="Getters and Setters">
  public String getFoo() {
    return foo;
  }

  public void setFoo(String foo) {
    this.foo = foo;
  }

  @Override
  public TaskType getType() {
    return TaskType.POC;
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
