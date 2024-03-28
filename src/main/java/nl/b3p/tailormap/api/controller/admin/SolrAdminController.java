/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;
import nl.b3p.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.repository.FeatureSourceRepository;
import nl.b3p.tailormap.api.repository.GeoServiceRepository;
import nl.b3p.tailormap.api.solr.SolrHelper;
import nl.b3p.tailormap.api.viewer.model.ErrorResponse;
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

  private final FeatureSourceRepository featureSourceRepository;

  private final GeoServiceRepository geoServiceRepository;

  public SolrAdminController(
      FeatureSourceFactoryHelper featureSourceFactoryHelper,
      FeatureSourceRepository featureSourceRepository,
      GeoServiceRepository geoServiceRepository) {
    this.featureSourceFactoryHelper = featureSourceFactoryHelper;
    this.featureSourceRepository = featureSourceRepository;
    this.geoServiceRepository = geoServiceRepository;
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
  @GetMapping(path = "${tailormap-api.admin.base-path}/index/ping")
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
   * @param geoserviceId the geoservice id
   * @param layerId the layer id
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
      responseCode = "500",
      description = "Error while indexing",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(example = "{\"message\":\"Some error message..\",\"code\":500}")))
  @Transactional
  @Timed(value = "index_feature_type", description = "time spent to index feature type")
  @PutMapping(path = "${tailormap-api.admin.base-path}/index/{geoserviceId}/{layerId}")
  public ResponseEntity<?> index(@PathVariable String geoserviceId, @PathVariable String layerId) {
    GeoService geoService =
        geoServiceRepository
            .findById(geoserviceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    TMFeatureType tmFeatureType =
        geoService.findFeatureTypeForLayer(geoService.findLayer(layerId), featureSourceRepository);
    if (tmFeatureType == null) {

      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              new ErrorResponse()
                  .message("Layer does not have feature type")
                  .code(HttpStatus.NOT_FOUND.value()));
    }

    try (SolrClient solrClient = getSolrClient();
        SolrHelper solrHelper = new SolrHelper(solrClient, featureSourceFactoryHelper)) {
      solrHelper.addFeatureTypeIndexForLayer(
          SolrHelper.getIndexLayerId(geoserviceId, layerId), tmFeatureType);
    } catch (UnsupportedOperationException | IOException | SolrServerException | SolrException e) {
      logger.error("Error indexing", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              new ErrorResponse()
                  .message(e.getLocalizedMessage())
                  .code(HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    logger.info("Indexing finished");
    // TODO if created return 201 if updated return 204
    return ResponseEntity.accepted().build();
  }

  /**
   * Clear index for the given service and layer. Since deleting a non-existing index is a no-op,
   * this will not cause an error and return.
   *
   * @param geoserviceId the geoservice id
   * @param layerName the layer id
   * @return the response entity ({@code 204 NOCONTENT} or an error response)
   */
  @Operation(
      summary = "Clear index for a feature type",
      description = "Clear index for the feature type associated with a layer")
  @ApiResponse(responseCode = "204", description = "Index cleared")
  @ApiResponse(
      responseCode = "500",
      description = "Error while clearing index",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(example = "{\"message\":\"Some error message..\",\"code\":500}")))
  @Timed(value = "index_delete", description = "time spent to delete an index of a feature type")
  @DeleteMapping(path = "${tailormap-api.admin.base-path}/index/{geoserviceId}/{layerName}")
  public ResponseEntity<?> clearIndex(
      @PathVariable String geoserviceId, @PathVariable String layerName) {
    try (SolrClient solrClient = getSolrClient();
        SolrHelper solrHelper = new SolrHelper(solrClient, featureSourceFactoryHelper)) {
      solrHelper.clearIndexForLayer(SolrHelper.getIndexLayerId(geoserviceId, layerName));
    } catch (IOException | SolrServerException e) {
      logger.error("Error clearing index", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              new ErrorResponse()
                  .message(e.getLocalizedMessage())
                  .code(HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    logger.info("Index cleared for layer {}:{}", geoserviceId, layerName);
    return ResponseEntity.noContent().build();
  }
}
