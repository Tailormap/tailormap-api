/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;
import static org.tailormap.api.controller.TestUrls.layerBegroeidTerreindeelPostgis;
import static org.tailormap.api.controller.TestUrls.layerProxiedWithAuthInPublicApp;

import com.github.romankh3.image.comparison.ImageComparison;
import com.github.romankh3.image.comparison.ImageComparisonUtil;
import com.github.romankh3.image.comparison.model.ImageComparisonResult;
import com.github.romankh3.image.comparison.model.ImageComparisonState;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.tailormap.api.annotation.PostgresIntegrationTest;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeoServiceProxyControllerIntegrationTest {
  private final String begroeidterreindeelUrl =
      "/app/default/layer/lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel/proxy/wms";

  private final String begroeidterreindeelLegendUrl =
      "/app/secured/layer/lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel/proxy/proxiedlegend";

  private final String pdokWmsProvinciegebiedLegendUrl =
      "/app/secured/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied/proxy/proxiedlegend";

  private final String obkUrl = "/app/secured/layer/lyr:openbasiskaart-proxied:osm/proxy/wmts";

  private final String pdokWmsGemeentegebiedUrl =
      "/app/default/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Gemeentegebied/proxy/wms";

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  private static final String expectedImagesPath =
      Objects.requireNonNull(GeoServiceProxyControllerIntegrationTest.class.getResource("./"))
          .getPath();

  @BeforeAll
  void initialize() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void test_proxied_legend_from_capabilities_unauthorized() throws Exception {
    final String path = apiBasePath + begroeidterreindeelLegendUrl;
    mockMvc
        .perform(get(path).param("SCALE", "693745.6953993673").with(setServletPath(path)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(username = "user")
  void test_proxied_legend_from_capabilities(TestInfo testInfo) throws Exception {
    final String path = apiBasePath + begroeidterreindeelLegendUrl;
    MvcResult result =
        mockMvc
            .perform(
                get(path)
                    .param("SCALE", "693745.6953993673")
                    .with(setServletPath(path))
                    .accept(MediaType.IMAGE_PNG))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition", "inline; filename=geoserver-GetLegendGraphic.image"))
            .andReturn();
    assertImagesEqual(
        expectedImagesPath + "begroeidterreindeelLegend_expected.png", result, testInfo);
  }

  @Test
  @WithMockUser(username = "user")
  void test_proxied_legend_from_capabilities2(TestInfo testInfo) throws Exception {
    final String path = apiBasePath + pdokWmsProvinciegebiedLegendUrl;
    MvcResult result =
        mockMvc
            .perform(get(path).with(setServletPath(path)))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
            .andExpect(header().string("Content-Length", new StringIsNotZeroMatcher()))
            .andReturn();

    assertImagesEqual(
        expectedImagesPath + "pdokWmsProvinciegebiedLegend_expected.png", result, testInfo);
  }

  private static void assertImagesEqual(
      String expectedImageFileName, MvcResult result, TestInfo testInfo) throws Exception {
    BufferedImage expectedImage = ImageComparisonUtil.readImageFromResources(expectedImageFileName);

    BufferedImage actualImage =
        ImageIO.read(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
    ImageIO.write(
        actualImage, "png", new File("./target/" + testInfo.getDisplayName() + "_actual.png"));

    ImageComparisonResult imageComparisonResult =
        new ImageComparison(expectedImage, actualImage)
            .setAllowingPercentOfDifferentPixels(5)
            .setDestination(new File("./target/" + testInfo.getDisplayName() + "_comparison.png"))
            .compareImages();

    assertEquals(ImageComparisonState.MATCH, imageComparisonResult.getImageComparisonState());
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void app_not_found_404() throws Exception {
    final String path = apiBasePath + "/app/1234/layer/76/proxy/wms";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Not Found"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void deny_non_proxied_service() throws Exception {
    final String path = apiBasePath + "/app/default/layer/lyr:snapshot-geoserver:BGT/proxy/wms";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Proxy not enabled for requested service"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void deny_wrong_protocol() throws Exception {
    final String path =
        apiBasePath
            + "/app/default/layer/lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel/proxy/wmts";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid proxy protocol: wmts"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void allow_http_post() throws Exception {
    final String path = apiBasePath + begroeidterreindeelUrl;
    mockMvc
        .perform(post(path).param("REQUEST", "GetCapabilities").with(setServletPath(path)))
        .andExpect(status().isOk());
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void allow_http_get() throws Exception {
    final String path = apiBasePath + begroeidterreindeelUrl;
    mockMvc
        .perform(get(path).param("REQUEST", "GetCapabilities").with(setServletPath(path)))
        .andExpect(status().isOk());
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void test_pdok_wms_GetCapabilities() throws Exception {
    final String path = apiBasePath + pdokWmsGemeentegebiedUrl;
    mockMvc
        .perform(
            get(path)
                .param("REQUEST", "GetCapabilities")
                .param("SERVICE", "WMS")
                .with(setServletPath(path))
                .header("User-Agent", "Pietje"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_XML))
        .andExpect(
            content()
                .string(
                    startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<WMS_Capabilities")));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void test_pdok_wms_GetMap() throws Exception {
    final String path = apiBasePath + pdokWmsGemeentegebiedUrl;
    mockMvc
        .perform(
            get(path)
                .param("REQUEST", "GetMap")
                .param("VERSION", "1.1.1")
                .param("FORMAT", "image/png")
                .param("TRANSPARENT", "TRUE")
                .param("LAYERS", "Gemeentegebied")
                .param("SRS", "EPSG:28992")
                .param("STYLES", "")
                .param("WIDTH", "1431")
                .param("HEIGHT", "1152")
                .param(
                    "BBOX",
                    "5864.898958858859,340575.3140154673,283834.9241914773,564349.9255234873")
                .param("SERVICE", "WMS")
                .with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
        .andExpect(header().string("Content-Length", new StringIsNotZeroMatcher()));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void test_pdok_wms_GetLegendGraphic() throws Exception {
    final String path = apiBasePath + pdokWmsGemeentegebiedUrl;
    mockMvc
        .perform(
            get(path)
                .param("REQUEST", "GetLegendGraphic")
                .param("VERSION", "1.1.1")
                .param("FORMAT", "image/png")
                .param("LAYER", "Gemeentegebied")
                .param("STYLE", "Gemeentegebied")
                .param("SCALE", "693745.6953993673")
                .param("SERVICE", "WMS")
                .with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
        .andExpect(header().string("Content-Length", new StringIsNotZeroMatcher()));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void test_wms_secured_proxy_not_in_public_app() throws Exception {
    final String path =
        apiBasePath + "/app/default/layer/lyr:openbasiskaart-proxied:osm/proxy/wmts";
    mockMvc
        .perform(
            get(path)
                .param("REQUEST", "GetCapabilities")
                .param("VERSION", "1.1.1")
                .with(setServletPath(path)))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Forbidden"));

    final String path2 = apiBasePath + layerProxiedWithAuthInPublicApp + "/proxy/wms";
    mockMvc
        .perform(
            get(path2)
                .param("REQUEST", "GetCapabilities")
                .param("VERSION", "1.1.0")
                .with(setServletPath(path2)))
        .andExpect(status().isForbidden());
  }

  private ResultActions performLoggedInRequiredAppLayerProxyRequest() throws Exception {
    final String path =
        apiBasePath
            + "/app/secured/layer/lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel/proxy/wms";
    return mockMvc.perform(
        get(path)
            .param("REQUEST", "GetCapabilities")
            .param("VERSION", "1.1.1")
            .with(setServletPath(path)));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void test_wms_secured_app_denied() throws Exception {
    performLoggedInRequiredAppLayerProxyRequest()
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.url").value("/login"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(username = "user")
  void test_wms_secured_app_granted() throws Exception {
    performLoggedInRequiredAppLayerProxyRequest()
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/vnd.ogc.wms_xml"))
        .andExpect(content().string(containsString("<WMT_MS_Capabilities version=\"1.1.1\"")));
  }

  @Test
  @WithMockUser(username = "user")
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void test_obk_wmts_GetCapabilities() throws Exception {
    final String path = apiBasePath + obkUrl;
    mockMvc
        .perform(
            get(path)
                .param("SERVICE", "WMTS")
                .param("REQUEST", "GetCapabilities")
                .param("VERSION", "1.0.0")
                .with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
        .andExpect(content().string(containsString("<TileMatrix")));
  }

  @Test
  @WithMockUser(username = "user")
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void test_obk_wmts_GetTile() throws Exception {
    final String path = apiBasePath + obkUrl;
    mockMvc
        .perform(
            get(path)
                .param("SERVICE", "WMTS")
                .param("VERSION", "1.0.0")
                .param("REQUEST", "GetTile")
                .param("LAYER", "osm")
                .param("STYLE", "default")
                .param("FORMAT", "image/png")
                .param("TILEMATRIXSET", "rd")
                .param("TILEMATRIX", "4")
                .param("TILEROW", "7")
                .param("TILECOL", "8")
                .with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
        .andExpect(header().string("Content-Length", new StringIsNotZeroMatcher()))
        .andExpect(header().exists("Last-Modified"));
  }

  @Test
  @WithMockUser(username = "user")
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void test_obk_wmts_GetTile_Conditional() throws Exception {
    final String path = apiBasePath + obkUrl;

    final DateTimeFormatter httpDateHeaderFormatter =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
            .withZone(ZoneId.of("GMT"));
    mockMvc
        .perform(
            get(path)
                .param("SERVICE", "WMTS")
                .param("VERSION", "1.0.0")
                .param("REQUEST", "GetTile")
                .param("LAYER", "osm")
                .param("STYLE", "default")
                .param("FORMAT", "image/png")
                .param("TILEMATRIXSET", "rd")
                .param("TILEMATRIX", "4")
                .param("TILEROW", "7")
                .param("TILECOL", "8")
                .with(setServletPath(path))
                .header("If-Modified-Since", httpDateHeaderFormatter.format(Instant.now())))
        .andExpect(status().isNotModified());
  }

  private static class StringIsNotZeroMatcher extends TypeSafeMatcher<String> {
    @Override
    protected boolean matchesSafely(String item) {
      return Long.parseLong(item) > 0;
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("is not zero");
    }
  }
}
