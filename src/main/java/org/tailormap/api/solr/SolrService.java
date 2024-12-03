/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.solr;

import java.util.concurrent.TimeUnit;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SolrService {
  @Value("${tailormap-api.solr-url}")
  private String solrUrl;

  @Value("${tailormap-api.solr-core-name:tailormap}")
  private String solrCoreName;

  @Value("${tailormap-api.solr-connection-timeout-seconds:60}")
  private int solrConnectionTimeout;

  @Value("${tailormap-api.solr-request-timeout-seconds:240}")
  private int solrRequestTimeout;

  @Value("${tailormap-api.solr-idle-timeout-seconds:10}")
  private int solrIdleTimeout;

  /**
   * Get a concurrent update Solr client for bulk operations.
   *
   * @return the Solr client
   */
  public SolrClient getSolrClientForIndexing() {
    return new Http2SolrClient.Builder(this.solrUrl + this.solrCoreName)
        .withFollowRedirects(true)
        .withConnectionTimeout(solrConnectionTimeout, TimeUnit.SECONDS)
        .withRequestTimeout(solrRequestTimeout, TimeUnit.SECONDS)
        // Set maxConnectionsPerHost for http1 connections,
        // maximum number http2 connections is limited to 4
        // .withMaxConnectionsPerHost(10)
        .withIdleTimeout(solrIdleTimeout, TimeUnit.SECONDS)
        .build();
  }

  /**
   * Get a Solr client for searching.
   *
   * @return the Solr client
   */
  public SolrClient getSolrClientForSearching() {
    return new Http2SolrClient.Builder(this.solrUrl + this.solrCoreName)
        .withConnectionTimeout(solrConnectionTimeout, TimeUnit.SECONDS)
        .withFollowRedirects(true)
        .build();
  }

  public void setSolrUrl(String solrUrl) {
    this.solrUrl = solrUrl;
  }
}
