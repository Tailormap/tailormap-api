/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;
import static org.tailormap.api.controller.TestUrls.layerBegroeidTerreindeelPostgis;
import static org.tailormap.api.controller.TestUrls.layerProxiedWithAuthInPublicApp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.tailormap.api.StaticTestData;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.viewer.model.ServerSentEventResponse;
import tools.jackson.databind.ObjectMapper;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
@Stopwatch
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LayerExtractControllerIntegrationTest {
  private static final String extractPath = "/extract/";
  private static final String downloadPath = "/extract/download/";
  // Use a unique clientId per test instance to avoid cross-test interference
  // when running concurrently.
  private final String sseClientId = "testcase-" + System.nanoTime();

  @Autowired
  private MockMvc mockMvc;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  /** SSE connection result; its response buffer accumulates server-sent events. */
  private MvcResult sseResult;

  @BeforeEach
  void start_sse_stream() throws Exception {
    final String sseUrl = apiBasePath + "/events/" + sseClientId;
    sseResult = mockMvc.perform(get(sseUrl)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .with(setServletPath(sseUrl))
            .acceptCharset(StandardCharsets.UTF_8))
        .andExpect(request().asyncStarted())
        .andReturn();
  }

  @Test
  void should_export_large_filter_to_csv() throws Exception {
    final String extractUrl = apiBasePath + layerBegroeidTerreindeelPostgis + extractPath + sseClientId;
    mockMvc.perform(post(extractUrl)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(extractUrl))
            .with(csrf())
            .param("attributes", "")
            .param("outputFormat", "csv")
            .param("filter", StaticTestData.get("large_cql_filter"))
            .acceptCharset(StandardCharsets.UTF_8)
            .characterEncoding(StandardCharsets.UTF_8)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .andExpect(status().isAccepted());

    // The SseEventBus may dispatch events slightly after the POST returns.
    // Awaitility polls the buffered SSE response until the expected content appears.
    Awaitility.await()
        .atMost(10, SECONDS)
        .untilAsserted(() -> assertThat(
            sseResult.getResponse().getContentAsString(), containsString("Extract task received")));

    Awaitility.await().pollInterval(5, SECONDS).atMost(30, SECONDS).untilAsserted(() -> {
      final String stream = sseResult.getResponse().getContentAsString();
      assertThat(count_completed_messages(stream), greaterThanOrEqualTo(1));
    });

    final String lastCompletedEventJson =
        getLastCompletedEventJson(sseResult.getResponse().getContentAsString());
    assertThat(lastCompletedEventJson.length(), greaterThanOrEqualTo(100));

    final String extractedDownloadId = getDownloadId(lastCompletedEventJson);
    assertThat(extractedDownloadId, containsString(".csv"));

    final String downloadUrl = apiBasePath + layerBegroeidTerreindeelPostgis + downloadPath + extractedDownloadId;
    MvcResult download = mockMvc.perform(get(downloadUrl).with(setServletPath(downloadUrl)))
        .andExpect(status().isOk())
        .andExpect(result -> {
          String contentType = result.getResponse().getContentType();
          assertThat(contentType, containsString("text/csv"));

          String contentDisposition = result.getResponse().getHeader("Content-Disposition");
          assertThat(contentDisposition, containsString("attachment; filename="));
          assertThat(contentDisposition, containsString(extractedDownloadId));
        })
        .andReturn();

    final String csvContent = download.getResponse().getContentAsString();
    assertEquals(
        19,
        csvContent.lines().count(),
        "Expected 19 lines in the CSV output, including header and 18 data rows");
  }

  @Test
  void should_export_large_output_to_csv() throws Exception {
    final String extractUrl = apiBasePath + layerBegroeidTerreindeelPostgis + extractPath + sseClientId;
    mockMvc.perform(post(extractUrl)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(extractUrl))
            .with(csrf())
            .param("attributes", "identificatie, class")
            .param("outputFormat", "csv")
            .acceptCharset(StandardCharsets.UTF_8)
            .characterEncoding(StandardCharsets.UTF_8)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .andExpect(status().isAccepted());

    // The SseEventBus may dispatch events slightly after the POST returns.
    // Awaitility polls the buffered SSE response until the expected content appears.
    Awaitility.await()
        .atMost(10, SECONDS)
        .untilAsserted(() -> assertThat(
            sseResult.getResponse().getContentAsString(), containsString("Extract task received")));

    Awaitility.await().pollInterval(5, SECONDS).atMost(5, MINUTES).untilAsserted(() -> {
      final String stream = sseResult.getResponse().getContentAsString();
      assertThat(count_completed_messages(stream), greaterThanOrEqualTo(1));
    });

    final String lastCompletedEventJson =
        getLastCompletedEventJson(sseResult.getResponse().getContentAsString());
    assertThat(lastCompletedEventJson.length(), greaterThanOrEqualTo(100));

    final String extractedDownloadId = getDownloadId(lastCompletedEventJson);
    assertThat(extractedDownloadId, containsString(".csv"));

    final String downloadUrl = apiBasePath + layerBegroeidTerreindeelPostgis + downloadPath + extractedDownloadId;
    MvcResult download = mockMvc.perform(get(downloadUrl).with(setServletPath(downloadUrl)))
        .andExpect(status().isOk())
        .andExpect(result -> {
          String contentType = result.getResponse().getContentType();
          assertThat(contentType, containsString("text/csv"));

          String contentDisposition = result.getResponse().getHeader("Content-Disposition");
          assertThat(contentDisposition, containsString("attachment; filename="));
          assertThat(contentDisposition, containsString(extractedDownloadId));
        })
        .andReturn();

    final String csvContent = download.getResponse().getContentAsString();
    assertEquals(
        3663,
        csvContent.lines().count(),
        "Expected 3663 lines in the CSV output, including header and 3662 data rows");
    csvContent.lines().findFirst().ifPresent(header -> {
      assertThat(header, containsString("identificatie"));
      assertThat(header, containsString("class"));
      // geometry is always included and the name is fixed
      assertThat(header, containsString("the_geom_wkt"));
      // these - among others - should not be exported
      assertThat(header, not(containsString("bronhouder")));
      assertThat(header, not(containsString("lv_publicatiedatum")));
    });
  }

  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  @Test
  void should_export_wfs_to_csv_with_authentication() throws Exception {
    final String extractUrl = apiBasePath + layerProxiedWithAuthInPublicApp + extractPath + sseClientId;
    mockMvc.perform(post(extractUrl)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(extractUrl))
            .with(csrf())
            .param("attributes", "geom,naam,code")
            .param("outputFormat", "csv")
            .acceptCharset(StandardCharsets.UTF_8)
            .characterEncoding(StandardCharsets.UTF_8)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .andExpect(status().isAccepted());

    // The SseEventBus may dispatch events slightly after the POST returns.
    // Awaitility polls the buffered SSE response until the expected content appears.
    Awaitility.await()
        .atMost(10, SECONDS)
        .untilAsserted(() -> assertThat(
            sseResult.getResponse().getContentAsString(), containsString("Extract task received")));

    Awaitility.await().pollInterval(5, SECONDS).atMost(5, MINUTES).untilAsserted(() -> {
      final String stream = sseResult.getResponse().getContentAsString();
      assertThat(count_completed_messages(stream), greaterThanOrEqualTo(1));
    });

    final String lastCompletedEventJson =
        getLastCompletedEventJson(sseResult.getResponse().getContentAsString());
    assertThat(lastCompletedEventJson.length(), greaterThanOrEqualTo(100));

    final String extractedDownloadId = getDownloadId(lastCompletedEventJson);
    assertThat(extractedDownloadId, containsString(".csv"));

    final String downloadUrl = apiBasePath + layerProxiedWithAuthInPublicApp + downloadPath + extractedDownloadId;
    MvcResult download = mockMvc.perform(get(downloadUrl).with(setServletPath(downloadUrl)))
        .andExpect(status().isOk())
        .andExpect(result -> {
          String contentType = result.getResponse().getContentType();
          assertThat(contentType, containsString("text/csv"));

          String contentDisposition = result.getResponse().getHeader("Content-Disposition");
          assertThat(contentDisposition, containsString("attachment; filename="));
          assertThat(contentDisposition, containsString(extractedDownloadId));
        })
        .andReturn();

    final String csvContent = download.getResponse().getContentAsString();
    assertEquals(
        13,
        csvContent.lines().count(),
        "Expected 13 lines in the CSV output, including header and 12 data rows");
    csvContent.lines().findFirst().ifPresent(header -> {
      // geometry is always included and the name is fixed/hardcoded
      assertThat(header, containsString("the_geom_wkt"));
      assertThat(header, containsString("naam"));
      assertThat(header, containsString("code"));
      assertThat(header, startsWith("\"the_geom_wkt\",\"naam\",\"code\""));
      assertThat(header, not(containsString("ligtInLandNaam")));
    });
  }

  @Test
  void should_export_large_filter_to_excel() throws Exception {
    final String extractUrl = apiBasePath + layerBegroeidTerreindeelPostgis + extractPath + sseClientId;
    mockMvc.perform(post(extractUrl)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(extractUrl))
            .with(csrf())
            .param("attributes", "")
            .param("outputFormat", "xlsx")
            .param("filter", StaticTestData.get("large_cql_filter"))
            .acceptCharset(StandardCharsets.UTF_8)
            .characterEncoding(StandardCharsets.UTF_8)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .andExpect(status().isAccepted());

    // The SseEventBus may dispatch events slightly after the POST returns.
    // Awaitility polls the buffered SSE response until the expected content appears.
    Awaitility.await()
        .atMost(10, SECONDS)
        .untilAsserted(() -> assertThat(
            sseResult.getResponse().getContentAsString(), containsString("Extract task received")));

    Awaitility.await().pollInterval(5, SECONDS).atMost(30, SECONDS).untilAsserted(() -> {
      final String stream = sseResult.getResponse().getContentAsString();
      assertThat(count_completed_messages(stream), greaterThanOrEqualTo(1));
    });

    final String lastCompletedEventJson =
        getLastCompletedEventJson(sseResult.getResponse().getContentAsString());
    assertThat(lastCompletedEventJson.length(), greaterThanOrEqualTo(100));

    final String extractedDownloadId = getDownloadId(lastCompletedEventJson);
    assertThat(extractedDownloadId, containsString(".xlsx"));

    final String downloadUrl = apiBasePath + layerBegroeidTerreindeelPostgis + downloadPath + extractedDownloadId;
    MvcResult download = mockMvc.perform(get(downloadUrl).with(setServletPath(downloadUrl)))
        .andExpect(status().isOk())
        .andExpect(result -> {
          String contentType = result.getResponse().getContentType();
          assertThat(
              contentType,
              containsString("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

          String contentDisposition = result.getResponse().getHeader("Content-Disposition");
          assertThat(contentDisposition, containsString("attachment; filename="));
          assertThat(contentDisposition, containsString(extractedDownloadId));
        })
        .andReturn();

    // open the Excel file and check that we have the expected content
    try (InputStream inp = new ByteArrayInputStream(download.getResponse().getContentAsByteArray());
        Workbook wb = WorkbookFactory.create(inp)) {
      Sheet sheet = wb.getSheetAt(0);

      assertEquals(
          18 + /*header row*/ 1,
          sheet.getPhysicalNumberOfRows(),
          () -> "Expected " + 18 + /*header row*/ 1
              + " rows in the Excel sheet, including header and 18 data rows");

      assertAll(
          "Check header and first data row",
          () -> assertEquals(
              "begroeidterreindeel",
              sheet.getSheetName(),
              "Expected sheet name to be begroeidterreindeel"),
          () -> assertEquals(
              14, sheet.getRow(0).getPhysicalNumberOfCells(), "Expected 14 columns in the header row"));

      assertAll(
          "Check first data row",
          () -> assertEquals(
              CellType.NUMERIC,
              sheet.getRow(1).getCell(0).getCellType(),
              "Expected first cell in header to be numeric (with date format)"),
          () -> assertEquals(
              CellType.STRING,
              sheet.getRow(1).getCell(1).getCellType(),
              "Expected second cell in header to be a string"),
          () -> assertEquals("geenWaarde", sheet.getRow(1).getCell(1).getStringCellValue()),
          () -> assertEquals("G0344", sheet.getRow(1).getCell(2).getStringCellValue()));
    }
  }

  /**
   * Parse the last non-empty line from the SSE stream that looks something like:
   * {@code data:{"details":{"message":"Extract task
   * completed","progress":100,"file":"begroeidterreindeel15061479295163305053.csv"},"eventType":"extract-completed","id":"019d6838-7f48-7053-9256-dd4b57c14264"}
   * } as JSON and extract the file from the details.
   */
  private String getLastCompletedEventJson(String sseMessages) throws IOException {
    return java.util.Arrays.stream(sseMessages.split("\\R"))
        .map(String::trim)
        .filter(line -> !line.isEmpty())
        .filter(line -> line.startsWith("data:"))
        .filter(line -> line.contains("\"eventType\":\"extract-completed\""))
        .reduce((first, second) -> second)
        .orElseThrow()
        .substring("data:".length());
  }

  private String getDownloadId(String eventJson) {
    return new ObjectMapper()
        .readTree(eventJson)
        .path("details")
        .path("downloadId")
        .asString();
  }

  private int count_completed_messages(String s) {
    int count = 0;
    int index = 0;
    final String marker = "\"eventType\":\"" + ServerSentEventResponse.EventTypeEnum.EXTRACT_COMPLETED + "\"";
    while ((index = s.indexOf(marker, index)) != -1) {
      count++;
      index += marker.length();
    }
    return count;
  }
}
