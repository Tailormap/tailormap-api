/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.common.SolrException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.persistence.SearchIndex;
import org.tailormap.api.persistence.TMFeatureSource;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.repository.FeatureTypeRepository;
import org.tailormap.api.repository.SearchIndexRepository;
import org.tailormap.api.scheduling.IndexTask;
import org.tailormap.api.scheduling.TMJobDataMap;
import org.tailormap.api.scheduling.Task;
import org.tailormap.api.scheduling.TaskManagerService;
import org.tailormap.api.scheduling.TaskType;
import org.tailormap.api.solr.SolrHelper;
import org.tailormap.api.solr.SolrService;
import org.tailormap.api.viewer.model.ErrorResponse;

/** Admin controller for Solr. */
@RestController
public class SolrAdminController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final FeatureTypeRepository featureTypeRepository;
  private final SearchIndexRepository searchIndexRepository;
  private final SolrService solrService;
  private final Scheduler scheduler;
  private final TaskManagerService taskManagerService;

  @Value("${tailormap-api.solr-query-timeout-seconds:7}")
  private int solrQueryTimeout;

  public SolrAdminController(
      @Autowired FeatureTypeRepository featureTypeRepository,
      @Autowired SearchIndexRepository searchIndexRepository,
      @Autowired SolrService solrService,
      @Autowired Scheduler scheduler,
      @Autowired TaskManagerService taskManagerService) {
    this.featureTypeRepository = featureTypeRepository;
    this.searchIndexRepository = searchIndexRepository;
    this.solrService = solrService;
    this.scheduler = scheduler;
    this.taskManagerService = taskManagerService;
  }

  @ExceptionHandler({ResponseStatusException.class})
  public ResponseEntity<?> handleException(ResponseStatusException ex) {
    // wrap the exception in a proper json response
    return ResponseEntity.status(ex.getStatusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new ErrorResponse()
                .message(ex.getReason() != null ? ex.getReason() : ex.getBody().getTitle())
                .code(ex.getStatusCode().value()));
  }

  /**
   * Ping solr.
   *
   * @return the response entity (ok or an error response)
   */
  @Operation(summary = "Ping Solr", description = "Ping Solr to check if it is available")
  @ApiResponse(
      responseCode = "200",
      description = "Solr is available",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(example = "{\"status\":\"OK\",\"timeElapsed\":1}")))
  @ApiResponse(
      responseCode = "500",
      description = "Solr is not available",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(example = "{\"message\":\"Some error message..\",\"code\":500}")))
  @GetMapping(
      path = "${tailormap-api.admin.base-path}/index/ping",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> pingSolr() {
    try (SolrClient solrClient = solrService.getSolrClientForSearching()) {
      final SolrPingResponse ping = solrClient.ping();
      logger.info("Solr ping status {}", ping.getResponse().get("status"));
      return ResponseEntity.ok(
          new ObjectMapper()
              .createObjectNode()
              .put("status", ping.getResponse().get("status").toString())
              .put("timeElapsed", ping.getElapsedTime()));
    } catch (IOException | SolrServerException e) {
      logger.error("Error pinging solr", e);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  /**
   * (re-) Index a layer.
   *
   * @param searchIndexId the searchIndex id
   * @return the response entity (accepted or an error response)
   */
  @Operation(
      summary = "Create or update a feature type index",
      description =
          "Create or update a feature type index for a layer, will erase existing index if present")
  @ApiResponse(
      responseCode = "202",
      description = "Index create or update request accepted",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema =
                  @Schema(
                      example =
                          "{\"type\":\"index\", \"uuid\":\"6308d26e-fe1e-4268-bb28-20db2cd06914\",\"code\":202}")))
  @ApiResponse(
      responseCode = "404",
      description = "Layer does not have feature type",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema =
                  @Schema(
                      example = "{\"message\":\"Layer does not have feature type\",\"code\":404}")))
  @ApiResponse(
      responseCode = "400",
      description = "Indexing WFS feature types is not supported",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema =
                  @Schema(
                      example =
                          "{\"message\":\"Layer does not have valid feature type for indexing\",\"code\":400}")))
  @ApiResponse(
      responseCode = "500",
      description = "Error while indexing",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(example = "{\"message\":\"Some error message..\",\"code\":500}")))
  @Transactional
  @Timed(value = "index_feature_type", description = "time spent to index feature type")
  @PutMapping(
      path = "${tailormap-api.admin.base-path}/index/{searchIndexId}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<?> index(@PathVariable Long searchIndexId) {
    SearchIndex searchIndex = validateInputAndFindIndex(searchIndexId);

    if (searchIndex.getStatus() == SearchIndex.Status.INDEXING) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Indexing already in progress, check tasks overview before retrying");
    }

    boolean createNewIndex =
        (null == searchIndex.getLastIndexed()
            || searchIndex.getStatus() == SearchIndex.Status.INITIAL);

    boolean hasSchedule =
        (null != searchIndex.getSchedule() && null != searchIndex.getSchedule().getUuid());

    UUID taskUuid;
    try {
      if (hasSchedule) {
        taskUuid = searchIndex.getSchedule().getUuid();
        startScheduledJobIndexing(searchIndex);
      } else {
        taskUuid = startOneTimeJobIndexing(searchIndex);
      }
      searchIndexRepository.save(searchIndex);
    } catch (UnsupportedOperationException
        | IOException
        | SolrServerException
        | SolrException
        | SchedulerException e) {
      logger.error("Error indexing", e);
      searchIndex.setStatus(SearchIndex.Status.ERROR);
      searchIndexRepository.save(searchIndex);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    logger.info(
        "Scheduled {} index for search index {}",
        (createNewIndex ? "creation of a new" : "update of"),
        searchIndex.getName());
    return ResponseEntity.accepted()
        .body(
            Map.of(
                "code",
                202,
                Task.TYPE_KEY,
                TaskType.INDEX.getValue(),
                Task.UUID_KEY,
                taskUuid,
                "message",
                "Indexing scheduled"));
  }

  private UUID startOneTimeJobIndexing(SearchIndex searchIndex)
      throws SolrServerException, IOException, SchedulerException {
    UUID taskName =
        taskManagerService.createTask(
            IndexTask.class,
            new TMJobDataMap(
                Map.of(
                    Task.TYPE_KEY,
                    TaskType.INDEX,
                    Task.DESCRIPTION_KEY,
                    "One-time indexing of " + searchIndex.getName(),
                    IndexTask.INDEX_KEY,
                    searchIndex.getId().toString(),
                    Task.PRIORITY_KEY,
                    0)));
    logger.info("One-time indexing job with UUID {} started", taskName);
    return taskName;
  }

  private void startScheduledJobIndexing(SearchIndex searchIndex) throws SchedulerException {
    JobKey jobKey =
        taskManagerService.getJobKey(TaskType.INDEX, searchIndex.getSchedule().getUuid());
    if (null == jobKey) {
      throw new SchedulerException("Indexing job not found in scheduler");
    }
    scheduler.triggerJob(jobKey);
    logger.info(
        "Indexing of scheduled job with UUID {} started", searchIndex.getSchedule().getUuid());
  }

  /**
   * Validate input and find the search index.
   *
   * @param searchIndexId the search index id
   * @return the search index
   * @throws ResponseStatusException if the search index is not found or the feature type is not
   *     found
   */
  private SearchIndex validateInputAndFindIndex(Long searchIndexId) {
    // check if solr is available
    this.pingSolr();

    // check if search index exists
    SearchIndex searchIndex =
        searchIndexRepository
            .findById(searchIndexId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Search index not found"));

    // check if feature type exists
    TMFeatureType indexingFT =
        featureTypeRepository
            .findById(searchIndex.getFeatureTypeId())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feature type not found"));

    if (TMFeatureSource.Protocol.WFS.equals(indexingFT.getFeatureSource().getProtocol())) {
      // the search index should not exist for WFS feature types, but test just in case
      searchIndex.setStatus(SearchIndex.Status.ERROR).setComment("WFS indexing not supported");
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Layer does not have valid feature type for indexing");
    }
    return searchIndex;
  }

  /**
   * Clear an index; does not remove the {@link SearchIndex} metadata.
   *
   * @param searchIndexId the searchindex id
   * @return the response entity ({@code 204 NOCONTENT} or an error response)
   */
  @Operation(
      summary = "Clear index for a feature type",
      description = "Clear index for the feature type")
  @ApiResponse(responseCode = "204", description = "Index cleared")
  @ApiResponse(responseCode = "404", description = "Index not configured for feature type")
  @ApiResponse(
      responseCode = "500",
      description = "Error while clearing index",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(example = "{\"message\":\"Some error message..\",\"code\":500}")))
  @Timed(value = "index_delete", description = "time spent to delete an index of a feature type")
  @DeleteMapping(
      path = "${tailormap-api.admin.base-path}/index/{searchIndexId}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Transactional
  public ResponseEntity<?> clearIndex(@PathVariable Long searchIndexId) {
    try (SolrClient solrClient = solrService.getSolrClientForSearching();
        SolrHelper solrHelper = new SolrHelper(solrClient).withQueryTimeout(solrQueryTimeout)) {
      solrHelper.clearIndexForLayer(searchIndexId);
      // do not delete the SearchIndex metadata object
      // searchIndexRepository.findById(searchIndexId).ifPresent(searchIndexRepository::delete);
      SearchIndex searchIndex =
          searchIndexRepository
              .findById(searchIndexId)
              .orElseThrow(
                  () ->
                      new ResponseStatusException(HttpStatus.NOT_FOUND, "Search index not found"));
      searchIndex
          .setLastIndexed(null)
          .setStatus(SearchIndex.Status.INITIAL)
          .setComment("Index cleared");
      searchIndexRepository.save(searchIndex);
    } catch (IOException | SolrServerException | NoSuchElementException e) {
      logger.warn("Error clearing index", e);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    logger.info("Index cleared for index {}", searchIndexId);
    return ResponseEntity.noContent().build();
  }
}
