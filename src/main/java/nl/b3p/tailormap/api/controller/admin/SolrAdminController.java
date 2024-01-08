/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller.admin;

import io.micrometer.core.annotation.Timed;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;
import nl.b3p.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.repository.FeatureSourceRepository;
import nl.b3p.tailormap.api.repository.GeoServiceRepository;
import nl.b3p.tailormap.api.solr.SolrUtil;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateHttp2SolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
  @GetMapping(path = "${tailormap-api.admin.base-path}/index/ping")
  public ResponseEntity<String> pingSolr() {
    try (SolrClient solrClient = getSolrClient()) {
      final SolrPingResponse ping = solrClient.ping();
      logger.info("Solr ping status {}", ping.getResponse().get("status"));
      return ResponseEntity.ok("solr ping status: " + ping.getResponse().get("status"));
    } catch (IOException | SolrServerException e) {
      logger.error("Error pinging solr", e);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  /**
   * (re-) Index a layer.
   *
   * @param geoserviceId the geoservice id
   * @param layerId the layer id
   * @return the response entity (accepted or an error response)
   */
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
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Layer does not have feature type");
    }

    try (SolrClient solrClient = getSolrClient();
        SolrUtil solrUtil = new SolrUtil(solrClient, featureSourceFactoryHelper)) {
      solrUtil.addFeatureTypeIndexForLayer(
          solrUtil.getLayerId(geoserviceId, layerId), tmFeatureType);
    } catch (UnsupportedOperationException | IOException | SolrServerException e) {
      logger.error("Error indexing", e);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    logger.info("Indexing finished");
    return ResponseEntity.accepted().build();
  }

  /**
   * Clear index for the given service and layer.
   *
   * @param geoserviceId the geoservice id
   * @param layerName the layer id
   * @return the response entity (accepted or an error response)
   */
  @Timed(value = "index_delete", description = "time spent to delete an index of a feature type")
  @DeleteMapping(path = "${tailormap-api.admin.base-path}/index/{geoserviceId}/{layerName}")
  public ResponseEntity<?> clearIndex(
      @PathVariable String geoserviceId, @PathVariable String layerName) {
    try (SolrClient solrClient = getSolrClient();
        SolrUtil solrUtil = new SolrUtil(solrClient, featureSourceFactoryHelper)) {
      solrUtil.clearIndexForLayer(solrUtil.getLayerId(geoserviceId, layerName));
    } catch (IOException | SolrServerException e) {
      logger.error("Error clearing index", e);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    logger.info("Index cleared");
    return ResponseEntity.accepted().build();
  }

  private SolrClient getSolrClient() {
    return new ConcurrentUpdateHttp2SolrClient.Builder(
            solrUrl + solrCoreName,
            new Http2SolrClient.Builder()
                .withFollowRedirects(true)
                .withConnectionTimeout(10000, TimeUnit.MILLISECONDS)
                .withRequestTimeout(60000, TimeUnit.MILLISECONDS)
                .build())
        .withQueueSize(SolrUtil.SOLR_BATCH_SIZE * 2)
        .withThreadCount(10)
        .build();
  }
}
