/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.pdok;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hamcrest.Matchers;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.tailormap.api.admin.model.CadastralMunicipality;

class CadastralMunicipalityClientTest {
  private static final String BASE_URL = "https://pdok.test/locatieserver/v3_1";

  private static final Pattern RANGE_FILTER = Pattern.compile(
      "kadastrale_gemeentecode:([\\[{])\"((?:[^\"\\\\]|\\\\.)*)\" TO (?:\"((?:[^\"\\\\]|\\\\.)*)\"}|\\*])");

  private final List<String> rangeFilters = Collections.synchronizedList(new ArrayList<>());

  /**
   * Create a client against a fake Locatieserver index. The function gets the parsed range filter and returns the
   * code and name of the next parcel, or {@code null} when none.
   */
  private CadastralMunicipalityClient clientFor(Function<Range, Map.Entry<String, String>> index) {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(ExpectedCount.manyTimes(), requestTo(Matchers.startsWith(BASE_URL + "/free?")))
        .andRespond(respondWith(index));
    return new CadastralMunicipalityClient(builder.build(), 4);
  }

  private CadastralMunicipalityClient clientFor(Map<String, String> codesAndNames) {
    TreeMap<String, String> index = new TreeMap<>(codesAndNames);
    return clientFor(range -> {
      Map.Entry<String, String> next =
          range.lowerInclusive() ? index.ceilingEntry(range.lower()) : index.higherEntry(range.lower());
      if (next == null || (range.upper() != null && next.getKey().compareTo(range.upper()) >= 0)) {
        return null;
      }
      return next;
    });
  }

  record Range(
      String lower, boolean lowerInclusive, @Nullable String upper) {}

  private ResponseCreator respondWith(Function<Range, Map.Entry<String, String>> index) {
    return request -> {
      MultiValueMap<String, String> params = decodedQueryParams(request.getURI());
      assertEquals("*:*", params.getFirst("q"));
      assertEquals("1", params.getFirst("rows"));
      assertEquals("kadastrale_gemeentecode asc", params.getFirst("sort"));
      assertEquals("kadastrale_gemeentecode,kadastrale_gemeentenaam", params.getFirst("fl"));
      List<String> filters = params.get("fq");
      assertEquals(2, filters.size());
      assertEquals("type:perceel", filters.get(0));
      String rangeFilter = filters.get(1);
      rangeFilters.add(rangeFilter);

      Matcher m = RANGE_FILTER.matcher(rangeFilter);
      assertTrue(m.matches(), "Invalid range filter: " + rangeFilter);
      Range range = new Range(
          unescape(m.group(2)), "[".equals(m.group(1)), m.group(3) == null ? null : unescape(m.group(3)));
      Map.Entry<String, String> next = index.apply(range);
      String docs = next == null
          ? ""
          : "{\"kadastrale_gemeentecode\":\"%s\",\"kadastrale_gemeentenaam\":\"%s\"}"
              .formatted(next.getKey().replace("\"", "\\\""), next.getValue());
      return withSuccess(
              "{\"response\":{\"numFound\":1,\"start\":0,\"numFoundExact\":true,\"docs\":[%s]}}"
                  .formatted(docs),
              MediaType.APPLICATION_JSON)
          .createResponse(request);
    };
  }

  private static MultiValueMap<String, String> decodedQueryParams(URI uri) {
    MultiValueMap<String, String> result = new LinkedMultiValueMap<>();
    UriComponentsBuilder.fromUri(uri)
        .build(true)
        .getQueryParams()
        .forEach((key, values) ->
            values.forEach(value -> result.add(key, URLDecoder.decode(value, StandardCharsets.UTF_8))));
    return result;
  }

  private static String unescape(String value) {
    return value.replace("\\\"", "\"").replace("\\\\", "\\");
  }

  @Test
  void fetches_all_codes_sorted_by_code() throws IOException {
    CadastralMunicipalityClient client = clientFor(Map.of(
        "ATN02", "Aalten",
        "ABG00", "Aalburg",
        "ASD02", "Amsterdam",
        "ASD03", "Amsterdam",
        "BRN00", "Baarn",
        "ZWL01", "Zwolle",
        "ZZZ99", "Last"));

    List<CadastralMunicipality> result = client.fetchAll();

    assertEquals(
        List.of("ABG00", "ASD02", "ASD03", "ATN02", "BRN00", "ZWL01", "ZZZ99"),
        result.stream().map(CadastralMunicipality::getCode).toList());
    assertEquals("Amsterdam", result.get(1).getName());
    // one request per code plus one ending request per chain
    assertEquals(7 + 26, rangeFilters.size());
  }

  @Test
  void first_request_of_a_chain_is_inclusive_and_next_requests_exclusive() throws IOException {
    clientFor(Map.of("ABG00", "Aalburg", "ATN02", "Aalten")).fetchAll();

    List<String> chainA =
        rangeFilters.stream().filter(f -> f.endsWith(" TO \"B\"}")).toList();
    assertEquals(
        List.of(
            "kadastrale_gemeentecode:[\"A\" TO \"B\"}",
            "kadastrale_gemeentecode:{\"ABG00\" TO \"B\"}",
            "kadastrale_gemeentecode:{\"ATN02\" TO \"B\"}"),
        chainA);
  }

  @Test
  void last_chain_has_no_upper_bound() throws IOException {
    clientFor(Map.of("ZWL01", "Zwolle")).fetchAll();

    assertTrue(rangeFilters.contains("kadastrale_gemeentecode:[\"Y\" TO \"Z\"}"));
    assertTrue(rangeFilters.contains("kadastrale_gemeentecode:[\"Z\" TO *]"));
    assertTrue(rangeFilters.contains("kadastrale_gemeentecode:{\"ZWL01\" TO *]"));
    assertTrue(rangeFilters.stream().noneMatch(f -> f.contains("\"[\"")));
  }

  @Test
  void invalid_codes_are_skipped_and_the_walk_continues_after_them() throws IOException {
    List<CadastralMunicipality> result = clientFor(Map.of(
            "ABG00", "Aalburg",
            "AB\"C1", "Invalid with quote",
            "ASD1", "Too short",
            "ATN02", "Aalten",
            "BRN00", " "))
        .fetchAll();

    assertEquals(
        List.of("ABG00", "ATN02"),
        result.stream().map(CadastralMunicipality::getCode).toList());
    assertTrue(rangeFilters.contains("kadastrale_gemeentecode:{\"AB\\\"C1\" TO \"B\"}"));
    assertTrue(rangeFilters.contains("kadastrale_gemeentecode:{\"ASD1\" TO \"B\"}"));
  }

  @Test
  void safety_cap_stops_a_chain_that_never_ends() {
    CadastralMunicipalityClient client =
        clientFor(range -> range.lower().startsWith("A") ? Map.entry("ABG00", "Aalburg") : null);

    IOException e = assertThrows(IOException.class, client::fetchAll);
    assertTrue(e.getMessage().contains("exceeded the maximum"), e.getMessage());
    long chainARequests =
        rangeFilters.stream().filter(f -> f.endsWith(" TO \"B\"}")).count();
    assertEquals(CadastralMunicipalityClient.MAX_REQUESTS_PER_CHAIN, chainARequests);
  }

  @Test
  void no_codes_at_all_is_an_error() {
    CadastralMunicipalityClient client = clientFor(Map.of());
    assertThrows(IOException.class, client::fetchAll);
  }

  @Test
  void server_error_is_an_error() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server.expect(ExpectedCount.manyTimes(), requestTo(Matchers.startsWith(BASE_URL + "/free?")))
        .andRespond(withServerError());
    CadastralMunicipalityClient client = new CadastralMunicipalityClient(builder.build(), 4);

    assertThrows(IOException.class, client::fetchAll);
  }
}
