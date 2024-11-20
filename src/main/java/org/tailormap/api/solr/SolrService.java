/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.solr;

import java.util.concurrent.TimeUnit;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateHttp2SolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SolrService {
  @Value("${tailormap-api.solr-url}")
  private String solrUrl;

  @Value("${tailormap-api.solr-core-name:tailormap}")
  private String solrCoreName;

  @Value("${tailormap-api.solr-queue-size:100}")
  private int solrQueueSize;

  @Value("${tailormap-api.solr-connection-timeout-millis:60000}")
  private int solrConnectionTimeout;

  @Value("${tailormap-api.solr-request-timeout-millis:10000}")
  private int solrRequestTimeout;

  @Value("${tailormap-api.solr-idle-timeout-millis:10000}")
  private int solrIdleTimeout;

  @Value("${tailormap-api.solr-queue-consumer-threads:10}")
  private int solrQueueThreads;

  /**
   * Get a concurrent update Solr client for bulk operations.
   *
   * @return the Solr client
   */
  public SolrClient getSolrClientForIndexing() {
    return new ConcurrentUpdateHttp2SolrClient.Builder(
            this.solrUrl + this.solrCoreName,
            new Http2SolrClient.Builder()
                .withFollowRedirects(true)
                .withConnectionTimeout(solrConnectionTimeout, TimeUnit.MILLISECONDS)
                .withRequestTimeout(solrRequestTimeout, TimeUnit.MILLISECONDS)
                // Set maxConnectionsPerHost for http1 connections,
                // maximum number http2 connections is limited to 4
                // .withMaxConnectionsPerHost(10)
                .withIdleTimeout(solrIdleTimeout, TimeUnit.MILLISECONDS)
                .build())
        /*
        The maximum number of requests buffered by the SolrClient's internal queue before being processed by background threads.
        This value should be carefully paired with the number of queue-consumer threads.
        A queue with a maximum size set too high may require more memory.
        A queue with a maximum size set too low may suffer decreased throughput as SolrClient.request(SolrRequest)
        calls block waiting to add requests to the queue.
        If not set, this defaults to 10.
        */
        .withQueueSize(solrQueueSize)
        /*
        The maximum number of threads used to empty ConcurrentUpdateHttp2SolrClients queue.
        Threads are created when documents are added to the client's internal queue and exit when no updates remain in the queue.
        This value should be carefully paired with the maximum queue capacity.
        A client with too few threads may suffer decreased throughput as the queue fills up and SolrClient.request(SolrRequest)
        calls block waiting to add requests to the queue. */
        .withThreadCount(solrQueueThreads)
        .build();
  }

  /**
   * Get a Solr client for searching.
   *
   * @return the Solr client
   */
  public SolrClient getSolrClientForSearching() {
    return new Http2SolrClient.Builder(this.solrUrl + this.solrCoreName)
        .withConnectionTimeout(solrConnectionTimeout, TimeUnit.MILLISECONDS)
        .withFollowRedirects(true)
        .build();
  }

  public void setSolrUrl(String solrUrl) {
    this.solrUrl = solrUrl;
  }
}
