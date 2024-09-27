/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller.admin;

import static java.net.HttpURLConnection.HTTP_ACCEPTED;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.invoke.MethodHandles;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin controller for controlling the task scheduler. Not to be used to create new tasks, adding
 * tasks belongs in the domain of the specific controller or Spring Data REST API as that requires
 * specific configuration information.
 */
@RestController
public class TaskAdminController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Operation(
      summary = "List all tasks, optionally filtered by type",
      description = "This will return a list of all tasks, optionally filtered by job type")
  @GetMapping(
      path = "${tailormap-api.admin.base-path}/tasks",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(
      responseCode = "200",
      description = "List of all tasks",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema =
                  @Schema(
                      example =
                          "{\"tasks\":[{\"uuid\":\"6308d26e-fe1e-4268-bb28-20db2cd06914\",\"type\":\"TestJob\"},{\"uuid\":\"d5ce9152-e90e-4b5a-b129-3b2366cabca8\",\"type\":\"label\"}]}")))
  public ResponseEntity<?> list(@RequestParam(required = false) String type) {
    logger.debug("Listing all tasks (optional type filter: {})", (null == type ? "all" : type));

    return ResponseEntity.ok(
        new ObjectMapper()
            .createObjectNode()
            .set(
                "tasks",
                new ObjectMapper()
                    .createArrayNode()
                    .add(
                        new ObjectMapper()
                            .createObjectNode()
                            .put("uuid", "6308d26e-fe1e-4268-bb28-20db2cd06914")
                            .put("type", "dummy"))
                    .add(
                        new ObjectMapper()
                            .createObjectNode()
                            .put("uuid", "d5ce9152-e90e-4b5a-b129-3b2366cabca8")
                            .put("type", "dummy"))));
  }

  @Operation(
      summary = "List all details for a given job",
      description =
          "This will return the details of the job, including the status, progress, result and any message")
  @GetMapping(
      path = "${tailormap-api.admin.base-path}/tasks/{uuid}",
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
      description = "Details of the job",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema =
                  @Schema(
                      example =
                          "{\"uuid\":\"6308d26e-fe1e-4268-bb28-20db2cd06914\",\"type\":\"TestJob\", \"status\":\"running\", \"progress\":0.5, \"result\":\"\", message:\"something is happening\"}")))
  public ResponseEntity<?> details(@PathVariable UUID uuid) {
    logger.debug("Getting job details for {}", uuid);

    return ResponseEntity.ok(
        new ObjectMapper()
            .createObjectNode()
            .put("uuid", "6308d26e-fe1e-4268-bb28-20db2cd06914")
            .put("type", "dummy")
            .put("status", "running")
            .put("progress", 0.5)
            .put("result", "")
            .put("message", "something is happening"));
  }

  @Operation(
      summary = "Start a job",
      description = "This will start the job if it is not already running")
  @PutMapping(
      path = "${tailormap-api.admin.base-path}/tasks/{uuid}/start",
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
  public ResponseEntity<?> startJob(@PathVariable UUID uuid) {
    logger.debug("Starting job {}", uuid);

    return ResponseEntity.status(HttpStatusCode.valueOf(HTTP_ACCEPTED))
        .body(new ObjectMapper().createObjectNode().put("message", "Job starting accepted"));
  }

  @Operation(
      summary = "Stop a job",
      description = "This will stop the job, if the job is not running, nothing will happen")
  @PutMapping(
      path = "${tailormap-api.admin.base-path}/tasks/{uuid}/stop",
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
  public ResponseEntity<?> stopJob(@PathVariable UUID uuid) {
    logger.debug("Stopping job {}", uuid);

    return ResponseEntity.status(HttpStatusCode.valueOf(HTTP_ACCEPTED))
        .body(new ObjectMapper().createObjectNode().put("message", "Job stopping accepted"));
  }

  @Operation(
      summary = "Delete a job",
      description =
          "This will remove the job from the scheduler and delete all information about the job")
  @DeleteMapping(
      path = "${tailormap-api.admin.base-path}/tasks/{uuid}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(
      responseCode = "404",
      description = "Job does not exist",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(example = "{\"message\":\"Job does not exist\"}")))
  @ApiResponse(responseCode = "204", description = "Job is deleted")
  public ResponseEntity<?> delete(@PathVariable UUID uuid) {
    logger.debug("Deleted job {}", uuid);

    return ResponseEntity.noContent().build();
  }
}
