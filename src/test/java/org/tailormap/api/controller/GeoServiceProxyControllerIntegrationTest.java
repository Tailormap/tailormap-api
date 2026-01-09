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
import org.gaul.modernizer_maven_annotations.SuppressModernizer;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junitpioneer.jupiter.DisabledUntil;
import org.junitpioneer.jupiter.Issue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.repository.GeoServiceRepository;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeoServiceProxyControllerIntegrationTest {
  private final String begroeidterreindeelUrl =
      "/app/default/layer/lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel/proxy/wms";

  private final String begroeidterreindeelLegendUrl =
      "/app/secured/layer/lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel/proxy/legend";

  private final String pdokWmsProvinciegebiedLegendUrl =
      "/app/secured/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied/proxy/legend";

  private final String obkUrl = "/app/secured/layer/lyr:openbasiskaart-proxied:osm/proxy/wmts";

  private final String pdokWmsGemeentegebiedUrl =
      "/app/default/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Gemeentegebied/proxy/wms";

  private final String pdok3dBasisvoorzieningGebouwenUrl =
      "/app/3d_utrecht/layer/lyr:3d_basisvoorziening_gebouwen_proxy:tiles3d/proxy/tiles3d";

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private GeoServiceRepository geoServiceRepository;

  private MockMvc mockMvc;

  private static final String expectedImagesPath = Objects.requireNonNull(
          GeoServiceProxyControllerIntegrationTest.class.getResource("./"))
      .getPath();

  @BeforeAll
  void initialize() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Test
  void proxied_legend_from_capabilities_unauthorized() throws Exception {
    final String path = apiBasePath + begroeidterreindeelLegendUrl;
    mockMvc.perform(get(path).param("SCALE", "693745.6953993673").with(setServletPath(path)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(username = "user")
  void proxied_legend_from_capabilities(TestInfo testInfo) throws Exception {
    final String path = apiBasePath + begroeidterreindeelLegendUrl;
    MvcResult result = mockMvc.perform(get(path)
            .param("SCALE", "693745.6953993673")
            .with(setServletPath(path))
            .accept(MediaType.IMAGE_PNG))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
        .andExpect(header().string("Content-Disposition", "inline; filename=geoserver-GetLegendGraphic.image"))
        .andReturn();
    assertImagesEqual(expectedImagesPath + "begroeidterreindeelLegend_expected.png", result, testInfo);
  }

  @DisabledUntil(date = "2025-04-03", reason = "PDOK WMS service is misconfigured, see issue HTM-1451")
  @Issue("https://b3partners.atlassian.net/browse/HTM-1451")
  @Test
  @WithMockUser(username = "user")
  void proxied_legend_from_capabilities2(TestInfo testInfo) throws Exception {
    final String path = apiBasePath + pdokWmsProvinciegebiedLegendUrl;
    MvcResult result = mockMvc.perform(get(path).with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
        .andExpect(header().string("Content-Length", new StringIsNotZeroMatcher()))
        .andReturn();

    assertImagesEqual(expectedImagesPath + "pdokWmsProvinciegebiedLegend_expected.png", result, testInfo);
  }

  @SuppressModernizer
  private static void assertImagesEqual(String expectedImageFileName, MvcResult result, TestInfo testInfo)
      throws Exception {
    BufferedImage expectedImage = ImageComparisonUtil.readImageFromResources(expectedImageFileName);

    BufferedImage actualImage =
        ImageIO.read(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
    ImageIO.write(actualImage, "png", new File("./target/" + testInfo.getDisplayName() + "_actual.png"));

    ImageComparisonResult imageComparisonResult = new ImageComparison(expectedImage, actualImage)
        .setAllowingPercentOfDifferentPixels(5)
        .setDestination(new File("./target/" + testInfo.getDisplayName() + "_comparison.png"))
        .compareImages();

    assertEquals(ImageComparisonState.MATCH, imageComparisonResult.getImageComparisonState());
  }

  @Test
  void app_not_found_404() throws Exception {
    final String path = apiBasePath + "/app/1234/layer/76/proxy/wms";
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Not Found"));
  }

  @Test
  void deny_non_proxied_service() throws Exception {
    final String path = apiBasePath + "/app/default/layer/lyr:snapshot-geoserver:BGT/proxy/wms";
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("Proxy not enabled for requested service"));
  }

  @Test
  void deny_wrong_protocol() throws Exception {
    final String path = apiBasePath
        + "/app/default/layer/lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel/proxy/wmts";
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid proxy protocol: wmts"));
  }

  @Test
  void allow_http_post() throws Exception {
    final String path = apiBasePath + begroeidterreindeelUrl;
    mockMvc.perform(post(path).param("REQUEST", "GetCapabilities").with(setServletPath(path)))
        .andExpect(status().isOk());
  }

  @Test
  void large_http_post() throws Exception {
    String largeCqlFilter =
        """
INTERSECTS(geom, LINESTRING(131948.07 458364.62,131948.07 458364.62,131951.11 458364.01,131952.33 458364.01,131957.2 458362.19,131963.28 458360.36,131965.72 458360.36,131968.15 458359.76,131977.28 458357.32,131981.54 458356.71,131985.79 458355.5,131993.7 458353.67,131995.53 458351.85,131997.35 458351.85,132002.22 458350.02,132003.44 458349.41,132007.09 458348.2,132008.31 458347.59,132009.52 458347.59,132011.35 458345.76,132011.96 458345.76,132012.57 458345.15,132013.17 458345.15,132013.78 458344.54,132014.39 458343.94,132016.22 458343.33,132017.43 458342.11,132018.65 458341.5,132022.3 458339.68,132024.73 458338.46,132026.56 458337.85,132031.43 458337.85,132033.86 458337.24,132037.51 458337.24,132039.34 458337.24,132041.16 458337.24,132043.6 458337.24,132044.81 458336.64,132046.03 458336.64,132049.07 458336.64,132050.29 458336.64,132051.5 458336.64,132053.33 458336.64,132053.94 458336.64,132053.94 458337.85,132053.94 458338.46,132053.94 458339.07,132052.72 458340.29,132052.72 458340.89,132052.11 458341.5,132049.68 458343.94,132048.46 458345.76,132047.25 458346.98,132041.77 458351.24,132038.12 458354.28,132030.82 458357.93,132027.17 458360.97,132023.52 458363.41,132015.61 458366.45,132011.35 458367.66,132008.31 458368.88,132000.4 458371.92,131997.96 458373.14,131993.1 458374.97,131991.27 458375.57,131989.45 458376.79,131986.4 458378.01,131985.19 458379.22,131983.97 458379.83,131980.93 458381.05,131979.71 458381.66,131978.49 458382.27,131976.67 458383.48,131975.45 458384.09,131973.63 458385.31,131971.8 458385.92,131971.19 458386.53,131969.98 458387.74,131969.37 458388.96,131968.76 458388.96,131968.15 458390.78,131967.54 458391.39,131966.93 458392,131966.93 458393.22,131966.33 458393.22,131966.33 458394.44,131965.72 458395.04,131965.72 458395.65,131965.72 458396.26,131965.72 458396.87,131965.72 458397.48,131965.72 458398.09,131965.72 458398.69,131967.54 458399.91,131968.76 458401.74,131970.58 458402.95,131971.8 458403.56,131973.02 458404.17,131976.06 458406,131976.67 458406.6,131977.89 458407.21,131979.71 458407.82,131980.32 458408.43,131980.93 458408.43,131982.14 458408.43,131982.75 458409.04,131983.36 458409.04,131983.97 458409.04,131984.58 458409.04,131985.79 458407.82,131986.4 458407.82,131988.23 458406.6,131989.45 458406,131990.66 458405.39,131993.1 458404.78,131994.31 458404.17,131995.53 458403.56,131996.75 458402.95,131998.57 458402.95,132001.61 458400.52,132003.44 458399.91,132004.66 458399.3,132005.26 458399.3,132008.31 458398.09,132013.17 458395.65,132015.61 458394.44,132020.48 458393.22,132021.08 458392,132022.3 458392,132024.73 458390.78,132026.56 458390.18,132028.38 458389.57,132028.99 458389.57,132030.21 458388.96,132032.04 458388.35,132033.25 458387.74,132035.69 458387.74,132036.29 458387.13,132036.9 458387.13,132039.34 458387.13,132039.94 458387.13,132041.77 458387.13,132044.81 458387.13,132046.64 458387.13,132049.07 458387.13,132053.33 458387.13,132055.76 458386.53,132060.02 458385.31,132061.24 458384.7,132062.46 458384.09,132064.89 458383.48,132066.72 458382.88,132067.93 458381.66,132070.37 458380.44,132071.58 458380.44,132072.19 458379.83,132073.41 458379.83,132073.41 458379.83,132074.63 458381.66,132075.23 458382.27,132075.23 458383.48,132076.45 458385.31,132076.45 458387.13,132077.06 458387.74,132077.06 458390.78,132077.06 458392,132077.06 458393.22,132077.06 458396.26,132077.06 458397.48,132076.45 458398.69,132075.23 458402.35,132074.63 458403.56,132072.19 458406.6,132071.58 458409.04,132070.97 458410.25,132069.76 458412.69,132069.15 458414.51,132068.54 458415.12,132067.32 458416.95,132066.11 458417.56,132064.28 458418.77,132063.06 458419.99,132061.85 458421.81,132058.81 458424.25,132057.59 458424.86,132056.37 458425.47,132052.72 458427.9,132050.9 458427.9,132048.46 458429.12,132046.64 458429.72,132045.42 458430.33,132042.38 458430.94,132040.55 458432.77,132038.73 458433.37,132035.08 458434.59,132032.64 458435.81,132031.43 458436.42,132028.38 458437.03,132027.78 458437.03,132025.95 458437.63,132025.34 458437.63,132024.73 458437.63,132023.52 458438.24,132022.91 458438.24,132022.3 458438.85,132021.69 458439.46,132021.69 458440.07,132021.08 458440.68,132021.08 458441.28,132020.48 458441.89,132020.48 458442.5,132019.87 458443.72,132019.26 458444.33,132017.43 458447.37,132017.43 458447.98,132016.82 458449.19,132016.22 458451.63,132015.61 458452.24,132015.61 458452.84,132015 458452.84,132015.61 458452.84,132016.82 458452.84,132019.26 458452.84,132022.3 458452.84,132032.64 458452.84,132038.12 458451.63,132044.81 458451.63,132061.85 458451.63,132070.97 458452.84,132086.19 458454.67,132095.31 458454.67,132101.4 458455.28,132114.17 458455.28,132120.26 458455.28,132124.52 458455.28,132132.43 458455.28,132134.86 458454.67,132139.73 458452.24,132141.55 458451.02,132143.38 458449.8,132147.64 458445.54,132149.46 458444.93,132150.68 458442.5,132153.72 458440.68,132154.33 458439.46,132154.94 458438.85,132155.55 458438.85,132155.55 458438.24,132155.55 458438.24,132155.55 458437.63,132156.15 458436.42,132156.76 458435.2,132157.37 458433.98,132157.37 458433.37,132157.37 458432.77,132157.37 458432.77,132157.37 458433.37,132157.37 458434.59,132157.37 458435.2,132157.37 458436.42,132157.37 458438.24,132157.37 458439.46,132157.37 458440.07,132154.94 458443.11,132153.11 458445.54,132151.9 458446.76,132148.85 458451.02,132146.42 458452.24,132144.59 458454.67,132140.34 458457.71,132139.12 458459.54,132134.86 458461.36,132133.03 458461.97,132131.21 458463.19,132128.17 458466.23,132126.95 458467.45,132122.69 458471.71,132120.26 458473.53,132117.82 458475.36,132113.56 458478.4,132111.13 458480.22,132109.91 458480.83,132107.48 458483.87,132106.26 458483.87,132105.05 458485.7,132103.83 458486.31,132102.61 458486.92,132098.96 458489.35,132096.53 458490.57,132093.49 458491.18,132085.58 458496.65,132081.32 458498.48,132078.88 458499.08,132073.41 458503.95,132070.97 458505.78,132067.93 458508.21,132065.5 458509.43,132064.89 458510.04,132063.67 458511.86,132062.46 458513.08,132061.85 458513.69,132060.02 458514.9,132059.41 458516.73,132058.81 458517.34,132056.98 458519.16,132056.37 458519.77,132055.76 458520.99,132055.76 458521.6,132055.16 458522.21,132055.16 458523.42,132055.16 458524.03,132054.55 458524.64,132054.55 458525.25,132054.55 458525.86,132054.55 458527.07,132053.94 458527.68,132053.94 458530.11,132053.94 458530.72,132053.94 458531.33,132053.94 458532.55,132054.55 458533.16,132054.55 458533.77,132055.76 458534.37,132056.37 458534.98,132059.41 458534.98,132061.24 458536.2,132062.46 458536.81,132063.06 458536.81,132065.5 458537.42,132067.32 458537.42,132068.54 458537.42,132072.8 458536.81,132076.45 458534.98,132079.49 458533.77,132089.23 458528.29,132094.09 458523.42,132107.48 458514.9,132115.39 458510.64,132121.47 458508.82,132134.86 458502.74,132140.94 458499.08,132147.03 458497.26,132157.37 458492.39,132162.85 458490.57,132167.11 458488.74,132177.45 458482.66,132182.92 458480.83,132190.23 458475.36,132193.27 458473.53,132194.48 458471.71,132197.53 458469.27,132198.74 458468.66,132199.35 458468.06,132199.96 458468.06,132200.57 458467.45,132200.57 458466.84,132200.57 458468.66,132199.96 458472.92,132199.35 458475.36,132199.35 458478.4,132197.53 458483.27,132197.53 458486.92,132195.7 458490.57,132195.09 458492.39,132194.48 458493.61,132193.88 458497.26,132193.27 458498.48,132192.66 458499.69,132190.23 458503.34,132189.62 458505.17,132187.18 458508.21,132186.58 458509.43,132185.36 458511.25,132182.92 458514.3,132182.32 458515.51,132181.71 458517.95,132179.27 458520.38,132178.67 458522.21,132177.45 458523.42,132175.02 458526.46,132174.41 458527.68,132172.58 458529.51,132171.36 458531.33,132170.15 458531.94,132168.32 458532.55,132167.11 458533.16,132165.89 458533.77,132164.06 458534.37,132162.24 458534.98,132160.41 458535.59,132159.2 458536.2,132157.98 458536.81,132154.33 458538.02,132153.11 458538.63,132151.9 458539.24,132147.03 458542.89,132144.59 458543.5,132138.51 458545.33,132134.86 458547.15,132126.34 458550.19,132122.69 458552.02,132112.96 458556.28,132108.7 458557.49,132105.65 458558.71,132101.4 458560.54,132100.79 458561.14,132098.35 458562.36,132097.75 458563.58,132097.75 458564.19,132096.53 458564.79,132096.53 458565.4,132095.92 458565.4,132095.92 458566.01,132095.31 458567.23,132095.31 458567.84,132094.7 458568.45,132094.7 458569.05,132094.09 458570.27,132094.09 458570.88,132094.09 458572.7,132094.09 458573.31,132094.09 458574.53,132094.09 458575.14,132094.09 458575.75,132094.09 458576.35,132094.09 458576.96,132094.09 458577.57,132094.09 458578.18))
""";

    final String path = apiBasePath + begroeidterreindeelUrl;
    mockMvc.perform(post(path)
            .param("REQUEST", "GetMap")
            .param("SERVICE", "WMS")
            .param("VERSION", "1.3.0")
            .param("FORMAT", "image/png")
            .param("STYLES", "")
            .param("TRANSPARENT", "TRUE")
            .param("LAYERS", "postgis:begroeidterreindeel")
            .param("WIDTH", "2775")
            .param("HEIGHT", "1002")
            .param("CRS", "EPSG:28992")
            .param("BBOX", "130574.85495843932,457818.25613033347,133951.6192003861,459037.5418133715")
            .param("CQL_FILTER", largeCqlFilter)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .with(setServletPath(path)))
        .andExpect(status().isOk());
  }

  @Test
  void allow_http_get() throws Exception {
    final String path = apiBasePath + begroeidterreindeelUrl;
    mockMvc.perform(get(path).param("REQUEST", "GetCapabilities").with(setServletPath(path)))
        .andExpect(status().isOk());
  }

  @Test
  void pdok_wms_get_capabilities() throws Exception {
    final String path = apiBasePath + pdokWmsGemeentegebiedUrl;
    mockMvc.perform(get(path)
            .param("REQUEST", "GetCapabilities")
            .param("SERVICE", "WMS")
            .with(setServletPath(path))
            .header("User-Agent", "Pietje"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_XML))
        .andExpect(
            content().string(startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<WMS_Capabilities")));
  }

  @Test
  void pdok_wms_get_map() throws Exception {
    final String path = apiBasePath + pdokWmsGemeentegebiedUrl;
    mockMvc.perform(get(path)
            .param("REQUEST", "GetMap")
            .param("VERSION", "1.1.1")
            .param("FORMAT", "image/png")
            .param("TRANSPARENT", "TRUE")
            .param("LAYERS", "Gemeentegebied")
            .param("SRS", "EPSG:28992")
            .param("STYLES", "")
            .param("WIDTH", "1431")
            .param("HEIGHT", "1152")
            .param("BBOX", "5864.898958858859,340575.3140154673,283834.9241914773,564349.9255234873")
            .param("SERVICE", "WMS")
            .with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
        .andExpect(header().string("Content-Length", new StringIsNotZeroMatcher()));
  }

  @Test
  void pdok_wms_get_legend_graphic() throws Exception {
    final String path = apiBasePath + pdokWmsGemeentegebiedUrl;
    mockMvc.perform(get(path)
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
  void wms_secured_proxy_not_in_public_app() throws Exception {
    final String path = apiBasePath + "/app/default/layer/lyr:openbasiskaart-proxied:osm/proxy/wmts";
    mockMvc.perform(get(path)
            .param("REQUEST", "GetCapabilities")
            .param("VERSION", "1.1.1")
            .with(setServletPath(path)))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Forbidden"));

    final String path2 = apiBasePath + layerProxiedWithAuthInPublicApp + "/proxy/wms";
    mockMvc.perform(get(path2)
            .param("REQUEST", "GetCapabilities")
            .param("VERSION", "1.1.0")
            .with(setServletPath(path2)))
        .andExpect(status().isForbidden());
  }

  private ResultActions performLoggedInRequiredAppLayerProxyRequest() throws Exception {
    final String path =
        apiBasePath + "/app/secured/layer/lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel/proxy/wms";
    return mockMvc.perform(get(path)
        .param("REQUEST", "GetCapabilities")
        .param("VERSION", "1.1.1")
        .with(setServletPath(path)));
  }

  @Test
  void wms_secured_app_denied() throws Exception {
    performLoggedInRequiredAppLayerProxyRequest()
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.url").value("/login"));
  }

  @Test
  @WithMockUser(username = "user")
  void wms_secured_app_granted() throws Exception {
    performLoggedInRequiredAppLayerProxyRequest()
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/vnd.ogc.wms_xml"))
        .andExpect(content().string(containsString("<WMT_MS_Capabilities version=\"1.1.1\"")));
  }

  @Test
  @WithMockUser(username = "user")
  void obk_wmts_get_capabilities() throws Exception {
    final String path = apiBasePath + obkUrl;
    mockMvc.perform(get(path)
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
  void obk_wmts_get_tile() throws Exception {
    final String path = apiBasePath + obkUrl;
    mockMvc.perform(get(path)
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
  void obk_wmts_get_tile_conditional() throws Exception {
    final String path = apiBasePath + obkUrl;

    final DateTimeFormatter httpDateHeaderFormatter = DateTimeFormatter.ofPattern(
            "EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
        .withZone(ZoneId.of("GMT"));
    mockMvc.perform(get(path)
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

  @Test
  @WithMockUser(username = "user")
  void get_3d_tiles_proxy() throws Exception {
    final String path = apiBasePath
        + pdok3dBasisvoorzieningGebouwenUrl
        + "/"
        + GeoServiceProxyController.TILES3D_DESCRIPTION_PATH;
    mockMvc.perform(get(path).with(setServletPath(path))).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(username = "user")
  void get_3d_tiles_proxy_subtree() throws Exception {
    final String path = apiBasePath + pdok3dBasisvoorzieningGebouwenUrl + "/subtrees/0/0/0.subtree";
    mockMvc.perform(get(path).with(setServletPath(path))).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(username = "user")
  void get_3d_tiles_proxy_tile() throws Exception {
    final String path = apiBasePath + pdok3dBasisvoorzieningGebouwenUrl + "/t/9/236/251.glb";
    mockMvc.perform(get(path).with(setServletPath(path))).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(username = "user")
  void get_3d_tiles_proxy_no_path() throws Exception {
    final String path = apiBasePath + pdok3dBasisvoorzieningGebouwenUrl;
    mockMvc.perform(get(path).with(setServletPath(path))).andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(username = "user")
  void get_3d_tiles_proxy_auth() throws Exception {
    final String path = apiBasePath
        + "/app/3d_utrecht/layer/lyr:3d_utrecht_proxied_auth:tiles3d/proxy/tiles3d"
        + "/"
        + GeoServiceProxyController.TILES3D_DESCRIPTION_PATH;
    mockMvc.perform(get(path).with(setServletPath(path))).andExpect(status().isOk());
  }

  @Test
  void get_3d_tiles_proxy_no_user() throws Exception {
    final String path = apiBasePath
        + "/app/3d_utrecht/layer/lyr:3d_utrecht_proxied_auth:tiles3d/proxy/tiles3d"
        + "/"
        + GeoServiceProxyController.TILES3D_DESCRIPTION_PATH;
    mockMvc.perform(get(path).with(setServletPath(path))).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(username = "user")
  @Transactional
  void get_3d_tiles_proxy_bad_password() throws Exception {
    GeoService geoService =
        geoServiceRepository.findById("3d_utrecht_proxied_auth").orElseThrow();
    String originalPassword = geoService.getAuthentication().getPassword();
    geoService.getAuthentication().setPassword("wrong_password");
    final String path = apiBasePath
        + "/app/3d_utrecht/layer/lyr:3d_utrecht_proxied_auth:tiles3d/proxy/tiles3d"
        + "/"
        + GeoServiceProxyController.TILES3D_DESCRIPTION_PATH;
    try {
      mockMvc.perform(get(path).with(setServletPath(path))).andExpect(status().isUnauthorized());
    } finally {
      geoService.getAuthentication().setPassword(originalPassword);
    }
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
