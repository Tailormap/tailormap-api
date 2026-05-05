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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;
import static org.tailormap.api.controller.TestUrls.layerOsmPolygonPostgis;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.util.IOUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junitpioneer.jupiter.Issue;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.tailormap.api.annotation.PostgresIntegrationTest;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
@Issue("HTM-2017: Large Excel export takes long time")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LayerExtractControllerLargeExcelIntegrationTest extends SseParsingUtils {
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

  @Stopwatch
  @Test
  void should_export_large_dataset_to_excel() throws Exception {
    final String extractUrl = apiBasePath + layerOsmPolygonPostgis + extractPath + sseClientId;
    mockMvc.perform(post(extractUrl)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(extractUrl))
            .with(csrf())
            .param(
                "attributes",
                "osm_id,access,addr:housename,addr:housenumber,addr:interpolation,admin_level,aerialway,aeroway,amenity,area,barrier,bicycle,brand,bridge,boundary,building,construction,covered,culvert,cutting,denomination,disused,embankment,foot,generator:source,harbour,highway,historic,horse,intermittent,junction,landuse,layer,leisure,lock,man_made,military,motorcar,name,natural,office,oneway,operator,place,population,power,power_source,public_transport,railway,ref,religion,route,service,shop,sport,surface,toll,tourism,tower:type,tracktype,tunnel,water,waterway,wetland,width,wood,z_order,way_area")
            .param("outputFormat", "xlsx")
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

    // should finish in less than 2 minutes
    Awaitility.await().pollInterval(5, SECONDS).atMost(2, MINUTES).untilAsserted(() -> {
      final String stream = sseResult.getResponse().getContentAsString();
      assertThat(count_completed_messages(stream), greaterThanOrEqualTo(1));
    });

    final String lastCompletedEventJson =
        getLastCompletedEventJson(sseResult.getResponse().getContentAsString());
    assertThat(lastCompletedEventJson.length(), greaterThanOrEqualTo(100));

    final String extractedDownloadId = getDownloadId(lastCompletedEventJson);
    assertThat(extractedDownloadId, containsString(".xlsx"));

    final String downloadUrl = apiBasePath + layerOsmPolygonPostgis + downloadPath + extractedDownloadId;
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
    // allow reading large files into byte arrays, this is 10x the default value
    int rememberMaxOverride = IOUtils.getByteArrayMaxOverride();
    IOUtils.setByteArrayMaxOverride(1_000_000_000);
    try (InputStream inp = new ByteArrayInputStream(download.getResponse().getContentAsByteArray());
        Workbook wb = WorkbookFactory.create(inp)) {

      Sheet sheet = wb.getSheetAt(0);

      assertAll(
          "Check sheet",
          () -> assertEquals(
              102467 + /*header row*/ 1,
              sheet.getPhysicalNumberOfRows(),
              () -> "Expected " + 102467 + /*header row*/ 1
                  + " rows in the Excel sheet, including header and data rows"),
          () -> assertEquals("osm_polygon", sheet.getSheetName(), "Expected sheet name to be osm_polygon"),
          () -> assertEquals(
              69, sheet.getRow(0).getPhysicalNumberOfCells(), "Expected 69 columns in the header row"));

      Map<String, Integer> columnNames = new HashMap<>();
      sheet.getRow(0).forEach(cell -> columnNames.put(cell.getStringCellValue(), cell.getColumnIndex()));

      assertAll(
          "Check first data row",
          () -> assertEquals(
              CellType.NUMERIC,
              sheet.getRow(1).getCell(columnNames.get("osm_id")).getCellType(),
              "Expected first cell in header to be numeric"),
          () -> assertEquals(
              CellType.BLANK,
              sheet.getRow(1).getCell(columnNames.get("access")).getCellType(),
              "Expected second cell in header to be a string"),
          () -> assertEquals(
              "meadow",
              sheet.getRow(1).getCell(columnNames.get("landuse")).getStringCellValue()),
          () -> assertEquals(
              68651.3,
              sheet.getRow(1).getCell(columnNames.get("way_area")).getNumericCellValue(),
              0.1));
    } finally {
      IOUtils.setByteArrayMaxOverride(rememberMaxOverride);
    }
  }
}
