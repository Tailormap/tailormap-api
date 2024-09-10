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
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateHttp2SolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.common.SolrException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.persistence.SearchIndex;
import org.tailormap.api.persistence.TMFeatureSource;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.repository.FeatureTypeRepository;
import org.tailormap.api.repository.SearchIndexRepository;
import org.tailormap.api.solr.SolrHelper;
import org.tailormap.api.viewer.model.ErrorResponse;

/** Admin controller for Solr. */
@RestController
public class SolrAdminController {
  @Value("${tailormap-api.solr-url}")
  private String solrUrl;

  @Value("${tailormap-api.solr-core-name:tailormap}")
  private String solrCoreName;

  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final FeatureSourceFactoryHelper featureSourceFactoryHelper;

  private final FeatureTypeRepository featureTypeRepository;
  private final SearchIndexRepository searchIndexRepository;

  public SolrAdminController(
      FeatureSourceFactoryHelper featureSourceFactoryHelper,
      FeatureTypeRepository featureTypeRepository,
      SearchIndexRepository searchIndexRepository) {
    this.featureSourceFactoryHelper = featureSourceFactoryHelper;
    this.featureTypeRepository = featureTypeRepository;
    this.searchIndexRepository = searchIndexRepository;
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
    try (SolrClient solrClient = getSolrClient()) {
      final SolrPingResponse ping = solrClient.ping();
      logger.info("Solr ping status {}", ping.getResponse().get("status"));
      return ResponseEntity.ok(
          new ObjectMapper()
              .createObjectNode()
              .put("status", ping.getResponse().get("status").toString())
              .put("timeElapsed", ping.getElapsedTime()));
    } catch (IOException | SolrServerException e) {
      logger.error("Error pinging solr", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              new ErrorResponse()
                  .message(e.getLocalizedMessage())
                  .code(HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }
  }

  /**
   * Get a concurrent update Solr client for bulk operations.
   *
   * @return the Solr client
   */
  private SolrClient getSolrClient() {
    return new ConcurrentUpdateHttp2SolrClient.Builder(
            solrUrl + solrCoreName,
            new Http2SolrClient.Builder()
                .withFollowRedirects(true)
                .withConnectionTimeout(10000, TimeUnit.MILLISECONDS)
                .withRequestTimeout(60000, TimeUnit.MILLISECONDS)
                .build())
        .withQueueSize(SolrHelper.SOLR_BATCH_SIZE * 2)
        .withThreadCount(10)
        .build();
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
  @ApiResponse(responseCode = "202", description = "Index create or update request accepted")
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
      responseCode = "404",
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

    SearchIndex searchIndex =
        searchIndexRepository
            .findById(searchIndexId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Search index not found"));

    TMFeatureType indexingFT =
        featureTypeRepository
            .findById(searchIndex.getFeatureTypeId())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feature type not found"));

    if (TMFeatureSource.Protocol.WFS.equals(indexingFT.getFeatureSource().getProtocol())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Layer does not have valid feature type for indexing");
    }

    boolean createNewIndex =
        (null == searchIndex.getLastIndexed()
            || searchIndex.getStatus() == SearchIndex.Status.INITIAL);
    try (SolrClient solrClient = getSolrClient();
        SolrHelper solrHelper = new SolrHelper(solrClient)) {
      solrHelper.addFeatureTypeIndex(searchIndex, indexingFT, featureSourceFactoryHelper);
      searchIndexRepository.save(searchIndex);
    } catch (UnsupportedOperationException | IOException | SolrServerException | SolrException e) {
      logger.error("Error indexing", e);
      searchIndex.setStatus(SearchIndex.Status.ERROR);
      searchIndexRepository.save(searchIndex);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              new ErrorResponse()
                  .message(e.getLocalizedMessage())
                  .code(HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    if (createNewIndex) {
      logger.info("Created new index for search index {}", searchIndexId);
      return ResponseEntity.status(HttpStatus.CREATED).build();
    } else {
      logger.info("Updated index for search index {}", searchIndexId);
      return ResponseEntity.accepted().build();
    }
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
    try (SolrClient solrClient = getSolrClient();
        SolrHelper solrHelper = new SolrHelper(solrClient)) {
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
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              new ErrorResponse()
                  .message(e.getLocalizedMessage())
                  .code(HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    logger.info("Index cleared for index {}", searchIndexId);
    return ResponseEntity.noContent().build();
  }
}
