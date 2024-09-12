/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import java.io.IOException;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
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
import org.tailormap.api.annotation.AppRestController;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.SearchIndex;
import org.tailormap.api.persistence.json.AppLayerSettings;
import org.tailormap.api.persistence.json.AppTreeLayerNode;
import org.tailormap.api.repository.SearchIndexRepository;
import org.tailormap.api.solr.SolrHelper;
import org.tailormap.api.solr.SolrService;
import org.tailormap.api.viewer.model.SearchResponse;

@AppRestController
@Validated
@RequestMapping(
    path = "${tailormap-api.base-path}/{viewerKind}/{viewerName}/layer/{appLayerId}/search",
    produces = MediaType.APPLICATION_JSON_VALUE)
public class SearchController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final SearchIndexRepository searchIndexRepository;
  private final SolrService solrService;

  @Value("${tailormap-api.pageSize:100}")
  private int numResultsToReturn;

  public SearchController(SearchIndexRepository searchIndexRepository, SolrService solrService) {
    this.searchIndexRepository = searchIndexRepository;
    this.solrService = solrService;
  }

  @Transactional(readOnly = true)
  @RequestMapping(method = {GET})
  @Timed(value = "search", description = "time spent to process search a request")
  @Counted(value = "search", description = "number of search calls")
  public ResponseEntity<Serializable> search(
      @ModelAttribute AppTreeLayerNode appTreeLayerNode,
      @ModelAttribute GeoService service,
      @ModelAttribute Application application,
      @RequestParam(required = false, name = "q") final String solrQuery,
      @RequestParam(required = false, defaultValue = "0") Integer start) {

    AppLayerSettings appLayerSettings = application.getAppLayerSettings(appTreeLayerNode);

    if (appLayerSettings.getSearchIndexId() == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "Layer '%s' does not have a search index".formatted(appTreeLayerNode.getLayerName()));
    }

    final SearchIndex searchIndex =
        searchIndexRepository
            .findById(appLayerSettings.getSearchIndexId())
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Layer '%s' does not have a search index"
                            .formatted(appTreeLayerNode.getLayerName())));

    try (SolrClient solrClient = solrService.getSolrClientForSearching();
        SolrHelper solrHelper = new SolrHelper(solrClient)) {
      final SearchResponse searchResponse =
          solrHelper.findInIndex(searchIndex, solrQuery, start, numResultsToReturn);
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
}
