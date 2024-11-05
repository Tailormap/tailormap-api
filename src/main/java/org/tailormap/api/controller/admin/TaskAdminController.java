/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller.admin;

import static java.net.HttpURLConnection.HTTP_ACCEPTED;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.TriggerUtils;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.spi.OperableTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.scheduling.Task;
import org.tailormap.api.scheduling.TaskManagerService;
import org.tailormap.api.scheduling.TaskType;

/**
 * Admin controller for controlling the task scheduler. Not to be used to create new tasks, adding
 * tasks belongs in the domain of the specific controller or Spring Data REST API as that requires
 * specific configuration information.
 */
@RestController
public class TaskAdminController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final Scheduler scheduler;
  private final TaskManagerService taskManagerService;

  public TaskAdminController(
      @Autowired Scheduler scheduler, @Autowired TaskManagerService taskManagerService) {
    this.scheduler = scheduler;
    this.taskManagerService = taskManagerService;
  }

  @Operation(
      summary = "List all tasks, optionally filtered by type",
      description =
          """
          This will return a list of all tasks, optionally filtered by task type.
          The state of the task is one of the Quartz Trigger states.
          The state can be one of: NONE, NORMAL, PAUSED, COMPLETE, ERROR, BLOCKED or null in error conditions.
          """)
  @GetMapping(
      path = "${tailormap-api.admin.base-path}/tasks",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(
      responseCode = "200",
      description = "List of all tasks, this list may be empty",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema =
                  @Schema(
                      example =
                          """
                          {"tasks":[
                          {"uuid":"6308d26e-fe1e-4268-bb28-20db2cd06914","type":"poc", "state":"NORMAL"},
                          {"uuid":"d5ce9152-e90e-4b5a-b129-3b2366cabca8","type":"poc", "state": "BLOCKED"},
                          {"uuid":"d5ce9152-e90e-4b5a-b129-3b2366cabca9","type":"poc", "state": "PAUSED"},
                          {"uuid":"d5ce9152-e90e-4b5a-b129-3b2366cabca2","type":"poc", "state": "COMPLETE"},
                          {"uuid":"d5ce9152-e90e-4b5a-b129-3b2366cabca3","type":"poc", "state": "ERROR"}
                          ]}
                          """)))
  public ResponseEntity<Object> list(@RequestParam(required = false) String type)
      throws ResponseStatusException {
    logger.debug("Listing all tasks (optional type filter: {})", (null == type ? "all" : type));
    final List<ObjectNode> tasks = new ArrayList<>();

    final GroupMatcher<JobKey> groupMatcher =
        (null == type ? GroupMatcher.anyGroup() : GroupMatcher.groupEquals(type));
    try {
      scheduler.getJobKeys(groupMatcher).stream()
          .map(
              jobKey -> {
                try {
                  return scheduler.getJobDetail(jobKey);
                } catch (SchedulerException e) {
                  logger.error("Error getting task detail", e);
                  return null;
                }
              })
          .filter(Objects::nonNull)
          .forEach(
              jobDetail -> {
                Trigger.TriggerState state;
                try {
                  state =
                      scheduler.getTriggerState(
                          TriggerKey.triggerKey(
                              jobDetail.getKey().getName(), jobDetail.getKey().getGroup()));
                } catch (SchedulerException e) {
                  logger.error("Error getting task state", e);
                  // ignore; to get a null (unknown) state
                  state = null;
                }
                tasks.add(
                    new ObjectMapper()
                        .createObjectNode()
                        .put(Task.UUID_KEY, jobDetail.getKey().getName())
                        .put(Task.TYPE_KEY, jobDetail.getKey().getGroup())
                        .put(
                            Task.DESCRIPTION_KEY,
                            jobDetail.getJobDataMap().getString(Task.DESCRIPTION_KEY))
                        .put("lastResult", jobDetail.getJobDataMap().getString("lastResult"))
                        .putPOJO(Task.STATE_KEY, state));
              });
    } catch (SchedulerException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error getting tasks", e);
    }

    return ResponseEntity.ok(
        new ObjectMapper()
            .createObjectNode()
            .set("tasks", new ObjectMapper().createArrayNode().addAll(tasks)));
  }

  @Operation(
      summary = "List all details for a given task",
      description =
          """
          This will return the details of the task, including the status, progress,
          result and any other information.
          """)
  @GetMapping(
      path = "${tailormap-api.admin.base-path}/tasks/{type}/{uuid}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(
      responseCode = "404",
      description = "Task not found",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(example = "{\"message\":\"Task not found\",\"code\":404}")))
  @ApiResponse(
      responseCode = "200",
      description = "Details of the task",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema =
                  @Schema(
                      example =
                          """
                          {
                            "uuid":"6308d26e-fe1e-4268-bb28-20db2cd06914",
                            "type":"poc",
                            "description":"This is a poc task",
                            "startTime":"2024-06-06T12:00:00Z",
                            "nextTime":"2024-06-06T12:00:00Z",
                            "jobData":{
                              "type":"poc",
                              "description":"This is a poc task"
                            },
                            "state":"NORMAL",
                            "progress":"TODO",
                            "result":"TODO",
                            "message":"TODO something is happening"
                          }
                          """)))
  public ResponseEntity<Object> details(@PathVariable TaskType type, @PathVariable UUID uuid)
      throws ResponseStatusException {
    logger.debug("Getting task details for {}:{}", type, uuid);

    try {
      JobKey jobKey = taskManagerService.getJobKey(type, uuid);
      if (null == jobKey) {
        return handleTaskNotFound();
      }

      JobDetail jobDetail = scheduler.getJobDetail(jobKey);
      JobDataMap jobDataMap = jobDetail.getJobDataMap();

      /* there should be only one */
      Trigger trigger = scheduler.getTriggersOfJob(jobDetail.getKey()).get(0);
      CronTrigger cron = ((CronTrigger) trigger);

      final Object[] result = new Object[1];
      scheduler.getCurrentlyExecutingJobs().stream()
          .filter(Objects::nonNull)
          .forEach(
              jobExecutionContext -> {
                logger.debug(
                    "currently executing job {} with trigger {}.",
                    jobExecutionContext.getJobDetail().getKey(),
                    jobExecutionContext.getTrigger().getKey());

                result[0] = jobExecutionContext.getResult();
              });

      return ResponseEntity.ok(
          new ObjectMapper()
              .createObjectNode()
              // immutable uuid, type and description
              .put(Task.UUID_KEY, jobDetail.getKey().getName())
              .put(Task.TYPE_KEY, jobDetail.getKey().getGroup())
              .put(Task.DESCRIPTION_KEY, jobDataMap.getString(Task.DESCRIPTION_KEY))
              .put(Task.CRON_EXPRESSION_KEY, cron.getCronExpression())
              // TODO / XXX we could add a human-readable description of the cron expression using
              // eg.
              //   com.cronutils:cron-utils like:
              //     CronParser cronParser = new
              //         CronParser(CronDefinitionBuilder.instanceDefinitionFor(QUARTZ));
              //     CronDescriptor.instance(locale).describe(cronParser.parse(cronExpression));
              //   this could also be done front-end using eg.
              // https://www.npmjs.com/package/cronstrue
              //   which has the advantage of knowing the required locale for the human
              // .put("cronDescription", cron.getCronExpression())
              .put("timezone", cron.getTimeZone().getID())
              .putPOJO("startTime", trigger.getStartTime())
              .putPOJO("lastTime", trigger.getPreviousFireTime())
              .putPOJO(
                  "nextFireTimes", TriggerUtils.computeFireTimes((OperableTrigger) cron, null, 5))
              .putPOJO(Task.STATE_KEY, scheduler.getTriggerState(trigger.getKey()))
              .putPOJO("progress", result[0])
              .put("lastResult", jobDataMap.getString("lastResult"))
              .putPOJO("jobData", jobDataMap));
    } catch (SchedulerException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error getting task", e);
    }
  }

  @Operation(
      summary = "Start a task",
      description = "This will start the task if it is not already running")
  @PutMapping(
      path = "${tailormap-api.admin.base-path}/tasks/{type}/{uuid}/start",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(
      responseCode = "404",
      description = "Task not found",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(example = "{\"message\":\"Task not found\",\"code\":404}")))
  @ApiResponse(
      responseCode = "202",
      description = "Task is started",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(example = "{\"message\":\"Task starting accepted\",\"code\":202}")))
  public ResponseEntity<Object> startTask(@PathVariable TaskType type, @PathVariable UUID uuid)
      throws ResponseStatusException {
    logger.debug("Starting task {}:{}", type, uuid);

    try {
      JobKey jobKey = taskManagerService.getJobKey(type, uuid);
      if (null == jobKey) {
        return handleTaskNotFound();
      }

      return ResponseEntity.status(HttpStatusCode.valueOf(HTTP_ACCEPTED))
          .body(
              new ObjectMapper().createObjectNode().put("message", "TODO: Task starting accepted"));

    } catch (SchedulerException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error getting task", e);
    }
  }

  @Operation(
      summary = "Stop a task",
      description = "This will stop the task, if the task is not running, nothing will happen")
  @PutMapping(
      path = "${tailormap-api.admin.base-path}/tasks/{type}/{uuid}/stop",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(
      responseCode = "404",
      description = "Task not found",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(example = "{\"message\":\"Task not found\", \"code\":404}")))
  @ApiResponse(
      responseCode = "202",
      description = "Task is stopping",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(example = "{\"message\":\"Task stopping accepted\"}")))
  public ResponseEntity<Object> stopTask(@PathVariable TaskType type, @PathVariable UUID uuid)
      throws ResponseStatusException {
    logger.debug("Stopping task {}:{}", type, uuid);

    try {
      JobKey jobKey = taskManagerService.getJobKey(type, uuid);
      if (null == jobKey) {
        return handleTaskNotFound();
      }

      return ResponseEntity.status(HttpStatusCode.valueOf(HTTP_ACCEPTED))
          .body(
              new ObjectMapper().createObjectNode().put("message", "TODO: Task stopping accepted"));

    } catch (SchedulerException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error getting task", e);
    }
  }

  @Operation(
      summary = "Delete a task",
      description =
          "This will remove the task from the scheduler and delete all information about the task")
  @DeleteMapping(
      path = "${tailormap-api.admin.base-path}/tasks/{type}/{uuid}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(
      responseCode = "404",
      description = "Task not found",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(example = "{\"message\":\"Task not found\"}")))
  @ApiResponse(responseCode = "204", description = "Task is deleted")
  public ResponseEntity<Object> delete(@PathVariable TaskType type, @PathVariable UUID uuid)
      throws ResponseStatusException {

    try {
      JobKey jobKey = taskManagerService.getJobKey(type, uuid);
      if (null == jobKey) {
        return handleTaskNotFound();
      }

      boolean succes = scheduler.deleteJob(jobKey);
      logger.info("Task {}:{} deletion {}", type, uuid, (succes ? "succeeded" : "failed"));

      return ResponseEntity.noContent().build();
    } catch (SchedulerException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error getting task", e);
    }
  }

  private ResponseEntity<Object> handleTaskNotFound() {
    return ResponseEntity.status(HttpStatusCode.valueOf(HTTP_NOT_FOUND))
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new ObjectMapper()
                .createObjectNode()
                .put("message", "Task not found")
                .put("code", HTTP_NOT_FOUND));
  }
}
