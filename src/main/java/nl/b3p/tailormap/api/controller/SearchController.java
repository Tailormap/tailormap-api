/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import java.io.IOException;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;
import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.persistence.json.AppTreeLayerNode;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayer;
import nl.b3p.tailormap.api.repository.FeatureSourceRepository;
import nl.b3p.tailormap.api.solr.SolrHelper;
import nl.b3p.tailormap.api.viewer.model.SearchResponse;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.common.SolrException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@AppRestController
@Validated
@RequestMapping(
    path = "${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/search",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class SearchController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final FeatureSourceFactoryHelper featureSourceFactoryHelper;

  private final FeatureSourceRepository featureSourceRepository;

  @Value("${tailormap-api.solr-url}")
  private String solrUrl;

  @Value("${tailormap-api.solr-core-name:tailormap}")
  private String solrCoreName;

  @Value("${tailormap-api.pageSize:100}")
  private int numResultsToReturn;

  public SearchController(
      FeatureSourceFactoryHelper featureSourceFactoryHelper,
      FeatureSourceRepository featureSourceRepository) {
    this.featureSourceFactoryHelper = featureSourceFactoryHelper;
    this.featureSourceRepository = featureSourceRepository;
  }

  @Transactional(readOnly = true)
  @RequestMapping(method = {GET})
  @Timed(value = "search", description = "time spent to process search a request")
  @Counted(value = "search", description = "number of search calls")
  public ResponseEntity<Serializable> search(
      @ModelAttribute AppTreeLayerNode appTreeLayerNode,
      @ModelAttribute GeoService service,
      @ModelAttribute GeoServiceLayer layer,
      @ModelAttribute Application application,
      @RequestParam(required = false) final String q,
      @RequestParam(required = false, defaultValue = "0") Integer start) {

    if (layer == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Can't find layer " + appTreeLayerNode);
    }
    TMFeatureType tmFeatureType = service.findFeatureTypeForLayer(layer, featureSourceRepository);
    if (tmFeatureType == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Layer does not have feature type");
    }

    try (SolrClient solrClient = getSolrClient();
        SolrHelper solrHelper = new SolrHelper(solrClient, featureSourceFactoryHelper)) {
      final SearchResponse searchResponse =
          solrHelper.findInIndex(
              SolrHelper.getIndexLayerId(service.getId(), layer.getName()),
              q,
              start,
              numResultsToReturn);

      return (null == searchResponse.getDocuments() || searchResponse.getDocuments().isEmpty())
          ? ResponseEntity.noContent().build()
          : ResponseEntity.ok().body(searchResponse);
    } catch (SolrServerException | IOException e) {
      logger.error("Error while contacting Solr", e);
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Error while searching", e);
    } catch (SolrException e) {
      logger.error("Error while searching", e);
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Error while searching with given query", e);
    }
  }

  private SolrClient getSolrClient() {
    return new Http2SolrClient.Builder(solrUrl + solrCoreName)
        .withConnectionTimeout(10, TimeUnit.SECONDS)
        .withFollowRedirects(true)
        .build();
  }
}
