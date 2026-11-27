/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.wfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.data.DataUtilities;
import org.geotools.data.wfs.WFSDataStoreFactory;
import org.geotools.factory.CommonFactoryFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WFSHitsCountUtilTest {
  @AfterEach
  void clearSlowHits() {
    WFSHitsCountUtil.clearSlowHits();
  }

  private static final String TYPE_NAME = "test:roads";
  private static final FilterFactory FF = CommonFactoryFinder.getFilterFactory();

  private static final String CAPABILITIES_2_0_0 = """
<?xml version="1.0" encoding="UTF-8"?>
<wfs:WFS_Capabilities version="2.0.0" xmlns:wfs="http://www.opengis.net/wfs/2.0"
xmlns:ows="http://www.opengis.net/ows/1.1" xmlns:fes="http://www.opengis.net/fes/2.0"
xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:test="http://test">
<ows:OperationsMetadata>
<ows:Operation name="GetCapabilities">
<ows:DCP><ows:HTTP><ows:Get xlink:href="{url}"/></ows:HTTP></ows:DCP>
</ows:Operation>
<ows:Operation name="DescribeFeatureType">
<ows:DCP><ows:HTTP><ows:Get xlink:href="{url}"/></ows:HTTP></ows:DCP>
<ows:Parameter name="outputFormat"><ows:AllowedValues><ows:Value>text/xml; subtype=gml/3.2</ows:Value></ows:AllowedValues></ows:Parameter>
</ows:Operation>
<ows:Operation name="GetFeature">
<ows:DCP><ows:HTTP><ows:Get xlink:href="{url}"/></ows:HTTP></ows:DCP>
<ows:Parameter name="outputFormat"><ows:AllowedValues><ows:Value>application/gml+xml; version=3.2</ows:Value></ows:AllowedValues></ows:Parameter>
<ows:Parameter name="resultType"><ows:AllowedValues>
<ows:Value>results</ows:Value><ows:Value>hits</ows:Value>
</ows:AllowedValues></ows:Parameter>
</ows:Operation>
</ows:OperationsMetadata>
<wfs:FeatureTypeList>
<wfs:FeatureType>
<wfs:Name>test:roads</wfs:Name>
<wfs:Title>Roads</wfs:Title>
<wfs:DefaultCRS>urn:ogc:def:crs:EPSG::28992</wfs:DefaultCRS>
<ows:WGS84BoundingBox>
<ows:LowerCorner>3.0 50.0</ows:LowerCorner><ows:UpperCorner>7.0 54.0</ows:UpperCorner>
</ows:WGS84BoundingBox>
</wfs:FeatureType>
</wfs:FeatureTypeList>
<fes:Filter_Capabilities>
<fes:Conformance>
<fes:Constraint name="ImplementsQuery"><ows:NoValues/><ows:DefaultValue>TRUE</ows:DefaultValue></fes:Constraint>
<fes:Constraint name="ImplementsAdHocQuery"><ows:NoValues/><ows:DefaultValue>TRUE</ows:DefaultValue></fes:Constraint>
<fes:Constraint name="ImplementsMinStandardFilter"><ows:NoValues/><ows:DefaultValue>TRUE</ows:DefaultValue></fes:Constraint>
<fes:Constraint name="ImplementsStandardFilter"><ows:NoValues/><ows:DefaultValue>TRUE</ows:DefaultValue></fes:Constraint>
</fes:Conformance>
<fes:Scalar_Capabilities>
<fes:LogicalOperators/>
<fes:ComparisonOperators>
<fes:ComparisonOperator name="PropertyIsEqualTo"/>
</fes:ComparisonOperators>
</fes:Scalar_Capabilities>
<fes:Spatial_Capabilities>
<fes:GeometryOperands><fes:GeometryOperand name="gml:Envelope"/></fes:GeometryOperands>
<fes:SpatialOperators><fes:SpatialOperator name="BBOX"/></fes:SpatialOperators>
</fes:Spatial_Capabilities>
</fes:Filter_Capabilities>
</wfs:WFS_Capabilities>
""";

  private static final String CAPABILITIES_1_1_0 = """
<?xml version="1.0" encoding="UTF-8"?>
<wfs:WFS_Capabilities version="1.1.0" xmlns:wfs="http://www.opengis.net/wfs"
xmlns:ows="http://www.opengis.net/ows" xmlns:ogc="http://www.opengis.net/ogc"
xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:test="http://test">
<ows:OperationsMetadata>
<ows:Operation name="GetCapabilities">
<ows:DCP><ows:HTTP><ows:Get xlink:href="{url}"/></ows:HTTP></ows:DCP>
</ows:Operation>
<ows:Operation name="DescribeFeatureType">
<ows:DCP><ows:HTTP><ows:Get xlink:href="{url}"/></ows:HTTP></ows:DCP>
<ows:Parameter name="outputFormat"><ows:Value>text/xml; subtype=gml/3.1.1</ows:Value></ows:Parameter>
</ows:Operation>
<ows:Operation name="GetFeature">
<ows:DCP><ows:HTTP><ows:Get xlink:href="{url}"/></ows:HTTP></ows:DCP>
<ows:Parameter name="outputFormat"><ows:Value>text/xml; subtype=gml/3.1.1</ows:Value></ows:Parameter>
<ows:Parameter name="resultType">
<ows:Value>results</ows:Value><ows:Value>hits</ows:Value>
</ows:Parameter>
</ows:Operation>
</ows:OperationsMetadata>
<wfs:FeatureTypeList>
<wfs:FeatureType>
<wfs:Name>test:roads</wfs:Name>
<wfs:Title>Roads</wfs:Title>
<wfs:DefaultSRS>urn:ogc:def:crs:EPSG::28992</wfs:DefaultSRS>
<ows:WGS84BoundingBox>
<ows:LowerCorner>3.0 50.0</ows:LowerCorner><ows:UpperCorner>7.0 54.0</ows:UpperCorner>
</ows:WGS84BoundingBox>
</wfs:FeatureType>
</wfs:FeatureTypeList>
<ogc:Filter_Capabilities>
<ogc:Spatial_Capabilities>
<ogc:GeometryOperands><ogc:GeometryOperand>gml:Envelope</ogc:GeometryOperand></ogc:GeometryOperands>
<ogc:SpatialOperators><ogc:SpatialOperator name="BBOX"/></ogc:SpatialOperators>
</ogc:Spatial_Capabilities>
<ogc:Scalar_Capabilities>
<ogc:LogicalOperators/>
<ogc:ComparisonOperators><ogc:ComparisonOperator>EqualTo</ogc:ComparisonOperator></ogc:ComparisonOperators>
</ogc:Scalar_Capabilities>
<ogc:Id_Capabilities><ogc:FID/></ogc:Id_Capabilities>
</ogc:Filter_Capabilities>
</wfs:WFS_Capabilities>
""";

  private static final String DESCRIBE_FEATURE_TYPE = """
<?xml version="1.0" encoding="UTF-8"?>
<xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:gml="http://www.opengis.net/gml/3.2"
xmlns:test="http://test" targetNamespace="http://test" elementFormDefault="qualified">
<xsd:import namespace="http://www.opengis.net/gml/3.2"
schemaLocation="http://schemas.opengis.net/gml/3.2.1/gml.xsd"/>
<xsd:complexType name="roadsType">
<xsd:complexContent>
<xsd:extension base="gml:AbstractFeatureType">
<xsd:sequence>
<xsd:element name="name" type="xsd:string" minOccurs="0"/>
<xsd:element name="geom" type="gml:GeometryPropertyType" minOccurs="0"/>
</xsd:sequence>
</xsd:extension>
</xsd:complexContent>
</xsd:complexType>
<xsd:element name="roads" type="test:roadsType" substitutionGroup="gml:AbstractFeature"/>
</xsd:schema>
""";

  private static final String HITS_2_0_0 = """
<?xml version="1.0" encoding="UTF-8"?>
<wfs:FeatureCollection xmlns:wfs="http://www.opengis.net/wfs/2.0"
numberMatched="%s" numberReturned="0" timeStamp="2026-11-25T12:00:00Z"/>
""";

  private static final String HITS_1_1_0 = """
<?xml version="1.0" encoding="UTF-8"?>
<wfs:FeatureCollection xmlns:wfs="http://www.opengis.net/wfs" numberOfFeatures="%s"
timeStamp="2026-11-25T12:00:00Z"/>
""";

  private static final String EXCEPTION_REPORT = """
<?xml version="1.0" encoding="UTF-8"?>
<ows:ExceptionReport xmlns:ows="http://www.opengis.net/ows/1.1" version="2.0.0">
<ows:Exception exceptionCode="NoApplicableCode"><ows:ExceptionText>Error</ows:ExceptionText></ows:Exception>
</ows:ExceptionReport>
""";

  private static int parse(String xml) throws Exception {
    return WFSHitsCountUtil.parseNumberMatched(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  @ParameterizedTest
  @CsvSource({"342,342", "0,0", "unknown,-1", "-5,-1", "'',-1"})
  void parse_number_matched(String numberMatched, int expected) throws Exception {
    assertEquals(expected, parse(HITS_2_0_0.formatted(numberMatched)));
  }

  @Test
  void parse_number_of_features() throws Exception {
    assertEquals(342, parse(HITS_1_1_0.formatted("342")));
  }

  @Test
  void parse_without_count() throws Exception {
    assertEquals(-1, parse("<wfs:FeatureCollection xmlns:wfs=\"http://www.opengis.net/wfs\"/>"));
  }

  @Test
  void parse_exception_report() throws Exception {
    assertEquals(-1, parse(EXCEPTION_REPORT));
  }

  /** Parse a response that sets numberMatched with an entity, returns -1 when it is not expanded. */
  private static int parseWithEntity(String doctype) {
    String xml = "<?xml version=\"1.0\"?>" + doctype
        + "<wfs:FeatureCollection xmlns:wfs=\"http://www.opengis.net/wfs/2.0\" numberMatched=\"&e;\"/>";
    try {
      return parse(xml);
    } catch (Exception e) {
      return -1;
    }
  }

  @Test
  void parse_does_not_load_external_dtd() throws Exception {
    // An external parameter entity which declares the entity used for numberMatched
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(response("application/xml-dtd", "<!ENTITY e \"42\">"));
      server.start();
      String url = server.url("/entities.dtd").toString();
      assertEquals(-1, parseWithEntity("<!DOCTYPE x [<!ENTITY % p SYSTEM \"" + url + "\"> %p;]>"));
      assertEquals(0, server.getRequestCount());
    }
  }

  @Test
  void parse_does_not_expand_internal_entity() {
    // Also prevents entity expansion attacks ("billion laughs")
    assertEquals(-1, parseWithEntity("<!DOCTYPE x [<!ENTITY e \"42\">]>"));
  }

  /** Serves a WFS and records the GetFeature requests. */
  private static class WFSDispatcher extends Dispatcher {
    private final String capabilities;
    private final Function<Map<String, String>, MockResponse> getFeatureResponse;
    private final List<Map<String, String>> getFeatureRequests = new ArrayList<>();
    private String url;

    WFSDispatcher(String capabilities, Function<Map<String, String>, MockResponse> getFeatureResponse) {
      this.capabilities = capabilities;
      this.getFeatureResponse = getFeatureResponse;
    }

    @Override
    public MockResponse dispatch(RecordedRequest request) {
      Map<String, String> params = new HashMap<>();
      for (String name : request.getUrl().queryParameterNames()) {
        params.put(name.toUpperCase(Locale.ROOT), request.getUrl().queryParameter(name));
      }
      String xml = "application/xml";
      return switch (params.getOrDefault("REQUEST", "")) {
        case "GetCapabilities" -> response(xml, capabilities.replace("{url}", url));
        case "DescribeFeatureType" -> response(xml, DESCRIBE_FEATURE_TYPE);
        case "GetFeature" -> {
          getFeatureRequests.add(params);
          yield getFeatureResponse.apply(params);
        }
        default -> new MockResponse.Builder().code(400).build();
      };
    }
  }

  private static MockResponse response(String contentType, String body) {
    return new MockResponse.Builder()
        .headers(new Headers(new String[] {"Content-Type", contentType}))
        .body(body)
        .build();
  }

  private record CountResult(int count, List<Map<String, String>> getFeatureRequests) {}

  private record CountsResult(List<Integer> counts, List<Map<String, String>> getFeatureRequests) {}

  private static CountResult countWithServer(
      String capabilities,
      Function<Map<String, String>, MockResponse> getFeatureResponse,
      String version,
      Query query)
      throws IOException {
    CountsResult result = countsWithServer(capabilities, getFeatureResponse, version, query, null, null, 1);
    return new CountResult(result.counts().getFirst(), result.getFeatureRequests());
  }

  /** Count {@code times} times with the same server, each time with a new data store like the API does. */
  private static CountsResult countsWithServer(
      String capabilities,
      Function<Map<String, String>, MockResponse> getFeatureResponse,
      String version,
      Query query,
      Duration hitsTimeout,
      Duration slowHitsTtl,
      int times)
      throws IOException {
    return countsWithServer(
        capabilities, getFeatureResponse, version, Collections.nCopies(times, query), hitsTimeout, slowHitsTtl);
  }

  /** Count once per query with the same server, each time with a new data store like the API does. */
  private static CountsResult countsWithServer(
      String capabilities,
      Function<Map<String, String>, MockResponse> getFeatureResponse,
      String version,
      List<Query> queries,
      Duration hitsTimeout,
      Duration slowHitsTtl)
      throws IOException {
    try (WFSDispatcher dispatcher = new WFSDispatcher(capabilities, getFeatureResponse);
        MockWebServer server = new MockWebServer()) {
      server.setDispatcher(dispatcher);
      server.start();
      dispatcher.url = server.url("/wfs").toString();
      Map<String, Object> params = new HashMap<>();
      params.put(
          WFSDataStoreFactory.URL.key,
          SimpleWFSHelper.getWFSRequestURL(dispatcher.url, "GetCapabilities", version, null)
              .toURL());
      params.put(WFSDataStoreFactory.PROTOCOL.key, false);
      List<Integer> counts = new ArrayList<>();
      for (Query query : queries) {
        DataStore ds = new WFSDataStoreFactory().createDataStore(params);
        try {
          SimpleFeatureSource fs = ds.getFeatureSource(ds.getTypeNames()[0]);
          counts.add(
              hitsTimeout == null
                  ? WFSHitsCountUtil.getCount(fs, query)
                  : WFSHitsCountUtil.getCount(fs, query, hitsTimeout, slowHitsTtl));
        } finally {
          ds.dispose();
        }
      }
      return new CountsResult(counts, dispatcher.getFeatureRequests);
    }
  }

  @Test
  void hits_request_for_wfs_2_0_0() throws Exception {

    CountResult result = countWithServer(
        CAPABILITIES_2_0_0,
        params -> response("application/gml+xml; version=3.2", HITS_2_0_0.formatted("342")),
        "2.0.0",
        Query.ALL);
    assertEquals(342, result.count());
    assertEquals(1, result.getFeatureRequests().size());
    Map<String, String> request = result.getFeatureRequests().getFirst();
    assertEquals("HITS", request.get("RESULTTYPE").toUpperCase(Locale.ROOT));
    assertEquals(TYPE_NAME, request.get("TYPENAMES"));
  }

  @Test
  void hits_request_for_wfs_2_0_0_with_filter() throws Exception {
    Query query = new Query(TYPE_NAME, FF.equals(FF.property("name"), FF.literal("A1")));
    CountResult result = countWithServer(
        CAPABILITIES_2_0_0,
        params -> response("application/gml+xml; version=3.2", HITS_2_0_0.formatted("12")),
        "2.0.0",
        query);
    assertEquals(12, result.count());
    assertEquals(1, result.getFeatureRequests().size());
    String filter = result.getFeatureRequests().getFirst().get("FILTER");
    assertTrue(filter.contains("PropertyIsEqualTo") && filter.contains("A1"), filter);
  }

  @Test
  void hits_request_unknown() throws Exception {

    CountResult result = countWithServer(
        CAPABILITIES_2_0_0,
        params -> response("application/gml+xml; version=3.2", HITS_2_0_0.formatted("unknown")),
        "2.0.0",
        Query.ALL);
    assertEquals(-1, result.count());
  }

  @Test
  void hits_request_exception_report() throws Exception {

    CountResult result = countWithServer(
        CAPABILITIES_2_0_0, params -> response("application/xml", EXCEPTION_REPORT), "2.0.0", Query.ALL);
    assertEquals(-1, result.count());
  }

  @Test
  void hits_request_http_error() throws Exception {

    CountResult result = countWithServer(
        CAPABILITIES_2_0_0,
        params -> new MockResponse.Builder().code(500).build(),
        "2.0.0",
        Query.ALL);
    assertEquals(-1, result.count());
  }

  @Test
  void hits_request_within_timeout() throws Exception {
    CountsResult result = countsWithServer(
        CAPABILITIES_2_0_0,
        params -> response("application/gml+xml; version=3.2", HITS_2_0_0.formatted("342")),
        "2.0.0",
        Query.ALL,
        Duration.ofSeconds(2),
        Duration.ofHours(1),
        2);
    assertEquals(List.of(342, 342), result.counts());
    // Not remembered as slow, counted again
    assertEquals(2, result.getFeatureRequests().size());
  }

  private static MockResponse slowHitsResponse() {
    return new MockResponse.Builder()
        .headers(new Headers(new String[] {"Content-Type", "application/gml+xml; version=3.2"}))
        .body(HITS_2_0_0.formatted("342"))
        .headersDelay(3, TimeUnit.SECONDS)
        .build();
  }

  @Test
  void slow_hits_request_times_out_and_is_not_repeated() throws Exception {
    CountsResult result = countsWithServer(
        CAPABILITIES_2_0_0,
        params -> slowHitsResponse(),
        "2.0.0",
        Query.ALL,
        Duration.ofSeconds(1),
        Duration.ofHours(1),
        2);
    assertEquals(List.of(-1, -1), result.counts());
    // The second time no hits request is done
    assertEquals(1, result.getFeatureRequests().size());
  }

  private static final Query SPATIAL_QUERY =
      new Query(TYPE_NAME, FF.bbox("geom", 100000, 400000, 200000, 500000, "EPSG:28992"));

  private static final Query ATTRIBUTE_QUERY = new Query(TYPE_NAME, FF.equals(FF.property("name"), FF.literal("A1")));

  /** Both an attribute and a spatial filter, remembered as spatial. */
  private static final Query ATTRIBUTE_AND_SPATIAL_QUERY =
      new Query(TYPE_NAME, FF.and(ATTRIBUTE_QUERY.getFilter(), SPATIAL_QUERY.getFilter()));

  /**
   * Counts with a server for which only the hits requests matching {@code slow} are slow. The delay (3 s) is well
   * over the timeout (1 s), the other requests are answered immediately, so the result doesn't depend on timing.
   */
  private static CountsResult countsWithSlowFilter(Predicate<String> slow, Query... queries) throws IOException {
    return countsWithServer(
        CAPABILITIES_2_0_0,
        params -> slow.test(params.getOrDefault("FILTER", ""))
            ? slowHitsResponse()
            : response("application/gml+xml; version=3.2", HITS_2_0_0.formatted("342")),
        "2.0.0",
        List.of(queries),
        Duration.ofSeconds(1),
        Duration.ofHours(1));
  }

  @Test
  void filter_kind() throws Exception {
    SimpleFeatureType schema = DataUtilities.createType(TYPE_NAME, "name:String,geom:Geometry");
    assertEquals(WFSHitsCountUtil.FilterKind.NONE, WFSHitsCountUtil.getFilterKind(null, schema));
    assertEquals(WFSHitsCountUtil.FilterKind.NONE, WFSHitsCountUtil.getFilterKind(Filter.INCLUDE, schema));
    assertEquals(
        WFSHitsCountUtil.FilterKind.ATTRIBUTE,
        WFSHitsCountUtil.getFilterKind(ATTRIBUTE_QUERY.getFilter(), schema));
    assertEquals(
        WFSHitsCountUtil.FilterKind.SPATIAL, WFSHitsCountUtil.getFilterKind(SPATIAL_QUERY.getFilter(), schema));
    assertEquals(
        WFSHitsCountUtil.FilterKind.SPATIAL,
        WFSHitsCountUtil.getFilterKind(ATTRIBUTE_AND_SPATIAL_QUERY.getFilter(), schema));
  }

  @Test
  void slow_spatial_hits_request_does_not_prevent_other_counts() throws Exception {
    // Like GeoServer on PostGIS with an untyped geometry column: only counting with a spatial filter is slow
    CountsResult result = countsWithSlowFilter(
        filter -> filter.contains("BBOX"),
        SPATIAL_QUERY,
        Query.ALL,
        ATTRIBUTE_QUERY,
        SPATIAL_QUERY,
        ATTRIBUTE_AND_SPATIAL_QUERY);
    assertEquals(List.of(-1, 342, 342, -1, -1), result.counts());
    // No hits request for the second spatial count and the attribute and spatial count
    assertEquals(3, result.getFeatureRequests().size());
  }

  @Test
  void slow_hits_request_without_filter_does_not_prevent_filtered_counts() throws Exception {
    // Like a large layer for which counting all features is slow, but filtering on an indexed column is quick
    CountsResult result =
        countsWithSlowFilter(String::isEmpty, Query.ALL, ATTRIBUTE_QUERY, SPATIAL_QUERY, Query.ALL);
    assertEquals(List.of(-1, 342, 342, -1), result.counts());
    assertEquals(3, result.getFeatureRequests().size());
  }

  @Test
  void slow_attribute_hits_request_does_not_prevent_other_counts() throws Exception {
    // Like a shapefile: the total is quick, but filtering on an attribute reads all records
    CountsResult result = countsWithSlowFilter(
        filter -> filter.contains("PropertyIsEqualTo") && !filter.contains("BBOX"),
        ATTRIBUTE_QUERY,
        Query.ALL,
        SPATIAL_QUERY,
        ATTRIBUTE_AND_SPATIAL_QUERY,
        ATTRIBUTE_QUERY);
    assertEquals(List.of(-1, 342, 342, 342, -1), result.counts());
    assertEquals(4, result.getFeatureRequests().size());
  }

  @Test
  void slow_hits_request_repeated_after_ttl() throws Exception {
    CountsResult result = countsWithServer(
        CAPABILITIES_2_0_0,
        params -> slowHitsResponse(),
        "2.0.0",
        Query.ALL,
        Duration.ofSeconds(1),
        Duration.ZERO,
        2);
    assertEquals(List.of(-1, -1), result.counts());
    assertEquals(2, result.getFeatureRequests().size());
  }

  @Test
  void slow_hits_request_without_timeout() throws Exception {
    CountResult result = countWithServer(
        CAPABILITIES_2_0_0,
        params -> new MockResponse.Builder()
            .headers(new Headers(new String[] {"Content-Type", "application/gml+xml; version=3.2"}))
            .body(HITS_2_0_0.formatted("342"))
            .headersDelay(1500, TimeUnit.MILLISECONDS)
            .build(),
        "2.0.0",
        Query.ALL);
    assertEquals(342, result.count());
  }

  @Test
  void no_extra_hits_request_for_wfs_1_1_0() throws Exception {

    CountResult result = countWithServer(
        CAPABILITIES_1_1_0,
        params -> response("text/xml; subtype=gml/3.1.1", HITS_1_1_0.formatted("342")),
        "1.1.0",
        Query.ALL);
    assertEquals(342, result.count());
    // Only the hits request GeoTools does itself
    assertEquals(1, result.getFeatureRequests().size());
  }
}
