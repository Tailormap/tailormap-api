/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junitpioneer.jupiter.Issue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
class GeoServiceProxyControllerPostgresIntegrationTest {
  private final String begroeidterreindeelUrl =
      "/app/default/layer/lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel/proxy/wms";
  private final String pdokWmsGemeentegebiedUrl =
      "/app/default/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Gemeentegebied/proxy/wms";
  private final String pdokWmsProvinciegebiedUrl =
      "/app/secured/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied/proxy/wms";

  @Autowired private MockMvc mockMvc;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  private static RequestPostProcessor requestPostProcessor(String servletPath) {
    return request -> {
      request.setServletPath(servletPath);
      return request;
    };
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void app_not_found_404() throws Exception {
    final String path = apiBasePath + "/app/1234/layer/76/proxy/wms";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(requestPostProcessor(path)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Not Found"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void deny_non_proxied_service() throws Exception {
    final String path = apiBasePath + "/app/default/layer/lyr:snapshot-geoserver:BGT/proxy/wms";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(requestPostProcessor(path)))
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
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(requestPostProcessor(path)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid proxy protocol: wmts"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void allow_http_post() throws Exception {
    final String path = apiBasePath + begroeidterreindeelUrl;
    mockMvc
        .perform(post(path).param("REQUEST", "GetCapabilities").with(requestPostProcessor(path)))
        .andExpect(status().isOk());
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void allow_http_get() throws Exception {
    final String path = apiBasePath + begroeidterreindeelUrl;
    mockMvc
        .perform(get(path).param("REQUEST", "GetCapabilities").with(requestPostProcessor(path)))
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
                .with(requestPostProcessor(path))
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
                .with(requestPostProcessor(path)))
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
                .with(requestPostProcessor(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
        .andExpect(header().string("Content-Length", new StringIsNotZeroMatcher()));
  }

  @Test
  @Disabled("Authentication-required is not working")
  // TODO: fix this test
  @Issue("https://b3partners.atlassian.net/browse/HTM-705")
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void test_wms_secured_proxy_not_in_public_app() throws Exception {
    final String path = apiBasePath + pdokWmsProvinciegebiedUrl;
    mockMvc
        .perform(
            get(path).param("REQUEST", "GetCapabilities").param("VERSION", "1.0.0"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Access denied"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(username = "noproxyuser")
  @Disabled("Authentication-required is not working")
  // TODO: fix this test
  @Issue("https://b3partners.atlassian.net/browse/HTM-705")
  void test_wms_secured_app_denied() throws Exception {
    mockMvc
        .perform(
            get("/app/6/layer/19/proxy/wms?Service=WMTS&Request=GetCapabilities&Version=1.0.0"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.url").value("/login"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "proxyuser",
      authorities = {"ProxyGroup"})
  @Disabled("Authentication-required is not working")
  // TODO: fix this test
  @Issue("https://b3partners.atlassian.net/browse/HTM-705")
  void test_wms_secured_app_granted() throws Exception {
    mockMvc
        .perform(get("/app/6/layer/19/proxy/wms?Service=WMS&Request=GetCapabilities&Version=1.0.0"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_XML))
        .andExpect(
            content()
                .string(
                    startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<WMS_Capabilities")));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void test_pdok_wmts_GetCapabilities() throws Exception {
    mockMvc
        .perform(
            get("/app/5/layer/16/proxy/wmts?Service=WMTS&Request=GetCapabilities&Version=1.0.0"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
        .andExpect(content().string(containsString("<TileMatrix")));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void test_pdok_wmts_GetTile() throws Exception {
    mockMvc
        .perform(
            get(
                "/app/5/layer/16/proxy/wmts?layer=Actueel_ortho25&style=default&tilematrixset=EPSG:28992&Service=WMTS&Request=GetTile&Version=1.0.0&Format=image/jpeg&TileMatrix=04&TileCol=8&TileRow=7"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG))
        .andExpect(header().string("Content-Length", new StringIsNotZeroMatcher()))
        .andExpect(header().exists("Last-Modified"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void test_pdok_wmts_GetTile_Conditional() throws Exception {
    final DateTimeFormatter httpDateHeaderFormatter =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
            .withZone(ZoneId.of("GMT"));
    mockMvc
        .perform(
            get("/app/5/layer/16/proxy/wmts?layer=Actueel_ortho25&style=default&tilematrixset=EPSG:28992&Service=WMTS&Request=GetTile&Version=1.0.0&Format=image/jpeg&TileMatrix=04&TileCol=8&TileRow=7")
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
