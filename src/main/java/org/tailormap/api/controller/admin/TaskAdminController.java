/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller.admin;

import static java.net.HttpURLConnection.HTTP_ACCEPTED;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Date;
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
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  public TaskAdminController(Scheduler scheduler) {
    this.scheduler = scheduler;
  }

  @Operation(
      summary = "List all tasks, optionally filtered by type",
      description = "This will return a list of all tasks, optionally filtered by task type")
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
                          "{\"tasks\":[{\"uuid\":\"6308d26e-fe1e-4268-bb28-20db2cd06914\",\"type\":\"TestJob\"},{\"uuid\":\"d5ce9152-e90e-4b5a-b129-3b2366cabca8\",\"type\":\"label\"}]}")))
  public ResponseEntity<?> list(@RequestParam(required = false) String type)
      throws SchedulerException {
    logger.debug("Listing all tasks (optional type filter: {})", (null == type ? "all" : type));
    List<ObjectNode> tasks = new ArrayList<>();

    GroupMatcher<JobKey> groupMatcher =
        (null == type ? GroupMatcher.anyGroup() : GroupMatcher.groupEquals(type));
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
              logger.debug("Job: {}", jobDetail.getKey());
              tasks.add(
                  new ObjectMapper()
                      .createObjectNode()
                      .put("uuid", jobDetail.getKey().getName())
                      .put("type", jobDetail.getJobDataMap().getString("type")));
            });

    return ResponseEntity.ok(
        new ObjectMapper()
            .createObjectNode()
            .set("tasks", new ObjectMapper().createArrayNode().addAll(tasks)));
  }

  @Operation(
      summary = "List all details for a given task",
      description =
          "This will return the details of the task, including the status, progress, result and any message")
  @GetMapping(
      path = "${tailormap-api.admin.base-path}/tasks/{type}/{uuid}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(
      responseCode = "404",
      description = "Job does not exist",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(example = "{\"message\":\"Job does not exist\",\"code\":404}")))
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
                            "status":"TODO",
                            "progress":"TODO",
                            "result":"TODO",
                            "message":"TODO something is happening"
                          }
                          """)))
  public ResponseEntity<?> details(@PathVariable String type, @PathVariable UUID uuid)
      throws SchedulerException {
    logger.debug("Getting task details for {}:{}", type, uuid);

    JobDetail details = scheduler.getJobDetail(getJobKey(type, uuid));
    JobDataMap jobDataMap = details.getJobDataMap();

    /* there should be only one */
    Trigger trigger = scheduler.getTriggersOfJob(details.getKey()).get(0);
    CronTrigger cron = ((CronTrigger) trigger);

    return ResponseEntity.ok(
        new ObjectMapper()
            .createObjectNode()
            .put("uuid", details.getKey().getName())
            .put("type", jobDataMap.getString("type"))
            .put("description", jobDataMap.getString("description"))
            // Date fields
            .putPOJO("startTime", trigger.getStartTime())
            .putPOJO("nextTime", trigger.getStartTime())
            .putPOJO("lastTime", trigger.getPreviousFireTime())
            // Cron fields
            .put("cronExpression", cron.getCronExpression())
            .putPOJO("nextFireTimes", getFireTimes(cron, 5))

            // TODO add status, progress, result and message etc. from jobDataMap
            .putPOJO("jobData", jobDataMap)
            .put("status", "TODO")
            .put("progress", "TODO")
            .put("result", "TODO")
            .put("message", "TODO something is happening"));
  }

  @Operation(
      summary = "Start a task",
      description = "This will start the task if it is not already running")
  @PutMapping(
      path = "${tailormap-api.admin.base-path}/tasks/{type}/{uuid}/start",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(
      responseCode = "404",
      description = "Job does not exist",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(example = "{\"message\":\"Job does not exist\",\"code\":404}")))
  @ApiResponse(
      responseCode = "202",
      description = "Job is started",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(example = "{\"message\":\"Job starting accepted\",\"code\":202}")))
  public ResponseEntity<?> startJob(@PathVariable String type, @PathVariable UUID uuid) {
    logger.debug("Starting task {}:{}", type, uuid);

    return ResponseEntity.status(HttpStatusCode.valueOf(HTTP_ACCEPTED))
        .body(new ObjectMapper().createObjectNode().put("message", "Job starting accepted"));
  }

  @Operation(
      summary = "Stop a task",
      description = "This will stop the task, if the task is not running, nothing will happen")
  @PutMapping(
      path = "${tailormap-api.admin.base-path}/tasks/{type}/{uuid}/stop",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(
      responseCode = "404",
      description = "Job does not exist",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(example = "{\"message\":\"Job does not exist\"}")))
  @ApiResponse(
      responseCode = "202",
      description = "Job is stopping",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(example = "{\"message\":\"Job stopping accepted\"}")))
  public ResponseEntity<?> stopJob(@PathVariable String type, @PathVariable UUID uuid) {
    logger.debug("Stopping task {}:{}", type, uuid);

    return ResponseEntity.status(HttpStatusCode.valueOf(HTTP_ACCEPTED))
        .body(new ObjectMapper().createObjectNode().put("message", "Job stopping accepted"));
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
      description = "Job does not exist",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(example = "{\"message\":\"Job does not exist\"}")))
  @ApiResponse(responseCode = "204", description = "Job is deleted")
  public ResponseEntity<?> delete(@PathVariable String type, @PathVariable UUID uuid)
      throws SchedulerException {

    boolean succes = scheduler.deleteJob(getJobKey(type, uuid));
    logger.info("Job {}:{} deletion {}", type, uuid, (succes ? "succeeded" : "failed"));

    return ResponseEntity.noContent().build();
  }

  /**
   * Get the job key for a given type and uuid.
   *
   * @param type the type of the job
   * @param uuid the uuid of the job
   * @return the job key
   * @throws SchedulerException when the scheduler cannot be reached
   * @throws ResponseStatusException when the job does not exist
   */
  private JobKey getJobKey(String type, UUID uuid)
      throws SchedulerException, ResponseStatusException {
    logger.debug("Finding job key for task {}:{}", type, uuid);
    return scheduler.getJobKeys(GroupMatcher.groupEquals(type)).stream()
        .filter(jobkey -> jobkey.getName().equals(uuid.toString()))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  private List<Date> getFireTimes(CronTrigger trigger, int count) {
    List<Date> fireTimes = new ArrayList<>(count);
    Date startTime = trigger.getStartTime();
    for (int i = 0; i < count; i++) {
      Date nextFireTime = trigger.getFireTimeAfter(startTime);
      if (nextFireTime == null) {
        break;
      }
      fireTimes.add(nextFireTime);
      startTime = nextFireTime;
    }
    return fireTimes;
  }
}
