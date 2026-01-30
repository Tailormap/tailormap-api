/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.prometheus.PrometheusResultProcessor;
import org.tailormap.api.prometheus.PrometheusService;
import org.tailormap.api.prometheus.TagNames;
import org.tailormap.api.repository.ApplicationRepository;
import org.tailormap.api.scheduling.PrometheusPingTask;
import org.tailormap.api.scheduling.TMJobDataMap;
import org.tailormap.api.scheduling.Task;
import org.tailormap.api.scheduling.TaskManagerService;
import org.tailormap.api.scheduling.TaskType;
import org.tailormap.api.viewer.model.ErrorResponse;

@RestController
public class PrometheusDataController implements TagNames, InitializingBean {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final PrometheusService prometheusService;
  private final PrometheusResultProcessor prometheusResultProcessor;
  private final ApplicationRepository applicationRepository;
  private final TaskManagerService taskManagerService;

  @Value("${tailormap-api.prometheus-api-appmetrics-totals}")
  private String totalsQuery;

  @Value("${tailormap-api.prometheus-api-appmetrics-updated}")
  private String counterLastUpdatedQuery;

  @Value("${tailormap-api.prometheus-api-appmetrics-layer-switched-on}")
  private String appLayersSwitchedOnTotalsQuery;

  @Value("${tailormap-api.prometheus-api-appmetrics-layer-switched-on-updated}")
  private String appLayersSwitchedOnLastUpdatedQuery;

  @Value("${tailormap-api.prometheus-api-ping-cron:0 0/5 * 1/1 * ? *}")
  private String prometheusPingCron;

  public PrometheusDataController(
      PrometheusService prometheusService,
      PrometheusResultProcessor prometheusResultProcessor,
      ApplicationRepository applicationRepository,
      TaskManagerService taskManagerService) {
    this.prometheusService = prometheusService;
    this.prometheusResultProcessor = prometheusResultProcessor;
    this.applicationRepository = applicationRepository;
    this.taskManagerService = taskManagerService;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    if (prometheusService.isPrometheusAvailable()) {
      logger.info("Prometheus is available, initializing PrometheusDataController.");
      try {
        // there should be only one Prometheus ping task, so we delete any existing ones
        taskManagerService.deleteTasksByGroupName(TaskType.PROMETHEUS_PING.getValue());
        final UUID taskUuid = taskManagerService.createTask(
            PrometheusPingTask.class,
            new TMJobDataMap(Map.of(
                Task.TYPE_KEY,
                TaskType.PROMETHEUS_PING.getValue(),
                Task.DESCRIPTION_KEY,
                "Ping Prometheus service for availability.",
                Task.PRIORITY_KEY,
                57)),
            prometheusPingCron);
        logger.debug("Added Prometheus ping task with UUID: {}", taskUuid);
      } catch (Exception e) {
        logger.error("Error initializing Prometheus ping task", e);
      }
    } else {
      logger.info("Prometheus is not available, /graph/ endpoint will not be functional.");
    }
  }

  @ExceptionHandler({ResponseStatusException.class})
  public ResponseEntity<?> handleException(ResponseStatusException ex) {
    return ResponseEntity.status(ex.getStatusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ErrorResponse()
            .message(
                ex.getReason() != null
                    ? ex.getReason()
                    : ex.getBody().getTitle())
            .code(ex.getStatusCode().value()));
  }

  @Operation(
      summary = "retrieve application graph data",
      description = "Fetches the totals and last updated time for application metrics from Prometheus.")
  @GetMapping(
      path = "${tailormap-api.admin.base-path}/graph/applications",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(
      responseCode = "200",
      description = "Array of application metrics with totals and last updated times.",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(example = """
{"applications":[
{"lastUpdateSecondsAgo":"85746","appName":"default","appId":"1","totalCount":"4003"},
{"lastUpdateSecondsAgo":"1345","appName":"test","appId":"5","totalCount":"5"}
]}
""")))
  public ResponseEntity<?> getApplicationGraphicData(@RequestParam(defaultValue = "30") int numberOfDays) {
    if (numberOfDays < 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid number of days provided.");
    }
    if (!prometheusService.isPrometheusAvailable()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Prometheus is not available.");
    }
    // we need to (at least) relabel the "__name__" query result type to avoid conflicts and omissions in the
    // flattened results
    final String completeQuery =
        "label_replace(" + totalsQuery.replace(NUMBER_OF_DAYS_REPLACE_TOKEN, String.valueOf(numberOfDays))
            + ", \"type\", \"totalCount\", \"__name__\", \".*\")"
            + " or " + "label_replace("
            + counterLastUpdatedQuery.replace(NUMBER_OF_DAYS_REPLACE_TOKEN, String.valueOf(numberOfDays))
            + ", \"type\", \"lastUpdateSecondsAgo\", \"__name__\", \".*\")";
    logger.trace("Fetching application graph data using query {}.", completeQuery);
    try {
      JsonNode totals = prometheusService.executeQuery(completeQuery);
      logger.trace("Application graph data fetched successfully. {}", totals.toPrettyString());
      Collection<Map<String, String>> applications =
          prometheusResultProcessor.processPrometheusResultsForApplications(totals);
      return ResponseEntity.ok(new ObjectMapper()
          .createObjectNode()
          .set("applications", new ObjectMapper().valueToTree(applications)));
    } catch (IOException e) {
      logger.error("Error fetching application graph data", e);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  @Operation(
      summary = "retrieve application layers graph data",
      description = "Fetches the totals and last updated time for application layer metrics from Prometheus.")
  @GetMapping(
      path = "${tailormap-api.admin.base-path}/graph/applayers/{applicationId}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(
      responseCode = "200",
      description = "Array of application layer metrics with totals and last updated times.",
      content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(example = """
{"applicationLayers":[
{"lastUpdateSecondsAgo":"3748","appLayerName":"osm","appName":"default","appId":"1","appLayerId":"lyr:openbasiskaart:osm","totalCount":"1973","appLayerTitle":"Openbasiskaart"},
{"lastUpdateSecondsAgo":"3748","appLayerName":"postgis:bak","appName":"default","appId":"1","appLayerId":"lyr:snapshot-geoserver:postgis:bak","totalCount":"2361","appLayerTitle":null},
{"lastUpdateSecondsAgo":"3748","appLayerName":"postgis:begroeidterreindeel","appName":"default","appId":"1","appLayerId":"lyr:snapshot-geoserver:postgis:begroeidterreindeel","totalCount":"2323","appLayerTitle":null},
{"lastUpdateSecondsAgo":"3748","appLayerName":"postgis:kadastraal_perceel","appName":"default","appId":"1","appLayerId":"lyr:snapshot-geoserver:postgis:kadastraal_perceel","totalCount":"1824","appLayerTitle":null}
]}
""")))
  public ResponseEntity<?> getApplicationLayersGraphicData(
      @PathVariable Long applicationId, @RequestParam(defaultValue = "30") int numberOfDays) {
    if (applicationId == null || applicationId <= 0 || numberOfDays < 1) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Invalid application id or number of days provided.");
      // not a problem to have an applicationId that does not exist, as the Prometheus query will
      // return an empty result set
    }
    if (!prometheusService.isPrometheusAvailable()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Prometheus is not available.");
    }
    // relabel the "__name__" query result type to avoid conflicts and omissions in the flattened results and
    // replace the APP_ID_REPLACE_TOKEN with the actual applicationId and NUMBER_OF_DAYS_REPLACE_TOKEN
    // with the actual numberOfDays
    String completeQuery = "label_replace("
        + appLayersSwitchedOnTotalsQuery
            .replace(APP_ID_REPLACE_TOKEN, applicationId.toString())
            .replace(NUMBER_OF_DAYS_REPLACE_TOKEN, String.valueOf(numberOfDays))
        + ", \"type\", \"totalCount\", \"__name__\", \".*\")"
        + " or "
        + "label_replace("
        + appLayersSwitchedOnLastUpdatedQuery
            .replace(APP_ID_REPLACE_TOKEN, applicationId.toString())
            .replace(NUMBER_OF_DAYS_REPLACE_TOKEN, String.valueOf(numberOfDays))
        + ", \"type\", \"lastUpdateSecondsAgo\", \"__name__\", \".*\")";
    logger.trace(
        "Fetching application layers graph data for applicationId: {} with query: {}",
        applicationId,
        completeQuery);
    try {
      JsonNode results = prometheusService.executeQuery(completeQuery);
      logger.trace("Application layers graph data fetched successfully. {}", results.toPrettyString());
      Collection<Map<String, String>> data =
          prometheusResultProcessor.processPrometheusResultsForApplicationLayers(results);
      if (!data.isEmpty()) {
        // enrich data with additional information (appLayerName and/or appLayerTitle)
        applicationRepository
            .findById(applicationId)
            .ifPresent(application -> data.forEach(metric -> {
              String appLayerId = metric.get(METRICS_APP_LAYER_ID_TAG);
              if (appLayerId != null && !appLayerId.isEmpty()) {
                application
                    .getAllAppTreeLayerNode()
                    .filter(node -> appLayerId.equals(node.getId()))
                    .findFirst()
                    .ifPresent(node -> {
                      metric.put(METRICS_APP_LAYER_NAME_TAG, node.getLayerName());
                      metric.put(
                          METRICS_APP_LAYER_TITLE_TAG,
                          application
                              .getAppLayerSettings(node)
                              .getTitle());
                    });
              }
            }));
      }

      return ResponseEntity.ok(new ObjectMapper()
          .createObjectNode()
          .set("applicationLayers", new ObjectMapper().valueToTree(data)));
    } catch (IOException e) {
      logger.error("Error fetching application layers graph data", e);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }
}
