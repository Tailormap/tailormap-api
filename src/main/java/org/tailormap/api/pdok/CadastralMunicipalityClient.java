/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.pdok;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.tailormap.api.admin.model.CadastralMunicipality;

/**
 * Client that builds the list of kadastrale gemeenten (cadastral municipalities) from the PDOK Locatieserver.
 *
 * <p>No PDOK API lists the kadastrale gemeenten directly, and the Locatieserver does not support facets or grouping. It
 * does support sorting and range filters, so this client walks through the parcels in the index sorted by
 * {@code kadastrale_gemeentecode}: each request asks for one parcel with a code greater than the last code found, which
 * returns the next distinct code. For example:
 *
 * <pre>
 * /free?q=*:*&amp;fq=type:perceel&amp;fq=kadastrale_gemeentecode:{"ATN02" TO "B"}
 *      &amp;sort=kadastrale_gemeentecode asc&amp;rows=1&amp;fl=kadastrale_gemeentecode,kadastrale_gemeentenaam
 * </pre>
 *
 * <p>The walk ends when no parcel is found. To speed it up, the key space is split into one chain per first letter (
 * <code>["A" TO "B"&#125;</code>, …, {@code ["Z" TO *]}) and the chains run in parallel. The first request of a chain
 * uses an inclusive lower bound, the next requests an exclusive lower bound on the last code found. A full walk takes
 * about 1,250 requests for about 1,200 codes.
 *
 * <p>Because the list comes from the same index the viewer searches parcels in, only codes that have parcels are
 * listed.
 */
@Component
public class CadastralMunicipalityClient {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /** AKR codes are three capital letters followed by two digits, e.g. {@code ATN02}. */
  static final Pattern AKR_CODE_PATTERN = Pattern.compile("[A-Z]{3}\\d{2}");

  /** Safety cap so a misbehaving response can't make a chain loop forever. The longest real chain is ~120. */
  static final int MAX_REQUESTS_PER_CHAIN = 1000;

  static final String CODE_FIELD = "kadastrale_gemeentecode";
  static final String NAME_FIELD = "kadastrale_gemeentenaam";

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

  private final RestClient restClient;
  private final int parallelRequests;

  @Autowired
  public CadastralMunicipalityClient(
      @Value("${tailormap-api.pdok.locatieserver.url:https://api.pdok.nl/bzk/locatieserver/search/v3_1}")
          String locatieserverUrl,
      @Value("${tailormap-api.pdok.cadastral-municipalities.parallel-requests:8}") int parallelRequests) {
    this(
        RestClient.builder()
            .baseUrl(locatieserverUrl)
            .requestFactory(createRequestFactory())
            .build(),
        parallelRequests);
  }

  CadastralMunicipalityClient(RestClient restClient, int parallelRequests) {
    this.restClient = restClient;
    this.parallelRequests = Math.max(1, parallelRequests);
  }

  private static JdkClientHttpRequestFactory createRequestFactory() {
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build());
    requestFactory.setReadTimeout(READ_TIMEOUT);
    return requestFactory;
  }

  /** A range of codes to walk through. A {@code null} upper bound means no upper bound. */
  record Chain(String lowerBound, @Nullable String upperBound) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record LocatieserverResponse(@Nullable Body response) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Body(@Nullable List<Doc> docs) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Doc(
      @JsonProperty(CODE_FIELD) @Nullable String code,
      @JsonProperty(NAME_FIELD) @Nullable String name) {}

  /**
   * Fetch all kadastrale gemeente codes that have parcels in the PDOK Locatieserver.
   *
   * @return the cadastral municipalities, sorted by code, never empty
   * @throws IOException when a request fails, a chain exceeds the safety cap, or no codes are found at all
   */
  public List<CadastralMunicipality> fetchAll() throws IOException {
    long start = System.currentTimeMillis();
    AtomicInteger requestCount = new AtomicInteger();
    List<CadastralMunicipality> result = new ArrayList<>();

    try (ExecutorService executor = Executors.newFixedThreadPool(parallelRequests)) {
      List<Future<List<CadastralMunicipality>>> futures = chains().stream()
          .map(chain -> executor.submit(() -> walkChain(chain, requestCount)))
          .toList();
      try {
        for (Future<List<CadastralMunicipality>> future : futures) {
          result.addAll(future.get());
        }
      } catch (ExecutionException e) {
        executor.shutdownNow();
        throw new IOException(
            "Error fetching cadastral municipalities from PDOK: " + e.getCause(), e.getCause());
      } catch (InterruptedException e) {
        executor.shutdownNow();
        Thread.currentThread().interrupt();
        throw new IOException("Interrupted while fetching cadastral municipalities from PDOK", e);
      }
    }

    if (result.isEmpty()) {
      throw new IOException("No cadastral municipalities found in PDOK Locatieserver");
    }
    result.sort(Comparator.comparing(CadastralMunicipality::getCode));
    logger.info(
        "Fetched {} cadastral municipalities from PDOK Locatieserver with {} requests in {} ms",
        result.size(),
        requestCount.get(),
        System.currentTimeMillis() - start);
    return List.copyOf(result);
  }

  /**
   * One chain per first letter: <code>["A" TO "B"&#125;</code>, …, <code>["Y" TO "Z"&#125;</code>, {@code ["Z" TO
   * *]}.
   */
  static List<Chain> chains() {
    List<Chain> chains = new ArrayList<>();
    for (char c = 'A'; c <= 'Z'; c++) {
      chains.add(new Chain(String.valueOf(c), c == 'Z' ? null : String.valueOf((char) (c + 1))));
    }
    return chains;
  }

  private List<CadastralMunicipality> walkChain(Chain chain, AtomicInteger requestCount) {
    List<CadastralMunicipality> result = new ArrayList<>();
    String cursor = chain.lowerBound();
    boolean inclusive = true;
    for (int i = 0; i < MAX_REQUESTS_PER_CHAIN; i++) {
      requestCount.incrementAndGet();
      Doc doc = fetchNext(rangeFilter(cursor, inclusive, chain.upperBound()));
      if (doc == null) {
        return result;
      }
      if (StringUtils.isEmpty(doc.code())) {
        throw new IllegalStateException("PDOK Locatieserver returned a parcel without " + CODE_FIELD);
      }
      if (AKR_CODE_PATTERN.matcher(doc.code()).matches() && StringUtils.isNotBlank(doc.name())) {
        result.add(new CadastralMunicipality().code(doc.code()).name(doc.name()));
      } else {
        logger.debug(
            "Skipping invalid cadastral municipality code \"{}\" (name \"{}\")", doc.code(), doc.name());
      }
      // also continue after an invalid code, so the walk does not get stuck on it
      cursor = doc.code();
      inclusive = false;
    }
    throw new IllegalStateException("Cadastral municipalities chain %s exceeded the maximum of %d requests"
        .formatted(chain, MAX_REQUESTS_PER_CHAIN));
  }

  private @Nullable Doc fetchNext(String rangeFilter) {
    LocatieserverResponse response = restClient
        .get()
        // Use URI variables, so the values (with spaces, quotes and braces) are fully encoded
        .uri(uriBuilder -> uriBuilder
            .path("/free")
            .queryParam("q", "{q}")
            .queryParam("fq", "{typeFilter}", "{rangeFilter}")
            .queryParam("sort", "{sort}")
            .queryParam("rows", "1")
            .queryParam("fl", "{fl}")
            .build(Map.of(
                "q",
                "*:*",
                "typeFilter",
                "type:perceel",
                "rangeFilter",
                rangeFilter,
                "sort",
                CODE_FIELD + " asc",
                "fl",
                CODE_FIELD + "," + NAME_FIELD)))
        .retrieve()
        .body(LocatieserverResponse.class);
    if (response == null || response.response() == null) {
      throw new IllegalStateException("Invalid response from PDOK Locatieserver: no response body");
    }
    List<Doc> docs = response.response().docs();
    return docs == null || docs.isEmpty() ? null : docs.getFirst();
  }

  /**
   * Create a Solr range filter on the code field. For example <code>kadastrale_gemeentecode:["A" TO "B"&#125;</code>
   * or <code>kadastrale_gemeentecode:&#123;"ZWL01" TO *]</code>.
   */
  static String rangeFilter(String lowerBound, boolean lowerInclusive, @Nullable String upperBound) {
    return CODE_FIELD
        + ":"
        + (lowerInclusive ? "[" : "{")
        + quote(lowerBound)
        + " TO "
        + (upperBound == null ? "*]" : quote(upperBound) + "}");
  }

  private static String quote(String value) {
    return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
  }
}
