/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.wfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.Headers;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.Query;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.filter.FilterFactory;
import org.geotools.data.wfs.WFSDataStore;
import org.geotools.data.wfs.WFSDataStoreFactory;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.http.HTTPClient;
import org.geotools.http.HTTPResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WFSHitsCountUtilTest {
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

  /** A GetFeature response with a single feature. */
  private static final String PAGE_2_0_0 = """
<?xml version="1.0" encoding="UTF-8"?>
<wfs:FeatureCollection xmlns:wfs="http://www.opengis.net/wfs/2.0" xmlns:gml="http://www.opengis.net/gml/3.2"
xmlns:test="http://test" numberMatched="%s" numberReturned="1" timeStamp="2026-11-25T12:00:00Z">
<wfs:member><test:roads gml:id="roads.1"><test:name>A1</test:name></test:roads></wfs:member>
</wfs:FeatureCollection>
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
    private final Function<String, MockResponse> getFeatureResponse;
    private final List<Map<String, String>> getFeatureRequests = new ArrayList<>();
    private String url;

    WFSDispatcher(String capabilities, Function<String, MockResponse> getFeatureResponse) {
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
          yield getFeatureResponse.apply(params.get("RESULTTYPE"));
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

  private static CountResult countWithServer(
      String capabilities, Function<String, MockResponse> getFeatureResponse, String version, Query query)
      throws IOException {
    return countWithServer(capabilities, getFeatureResponse, version, query, false);
  }

  /** @param withPage whether to retrieve a page of features before counting, like the FeaturesController */
  private static CountResult countWithServer(
      String capabilities,
      Function<String, MockResponse> getFeatureResponse,
      String version,
      Query query,
      boolean withPage)
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
      // The factory used by WFSFeatureSourceHelper
      DataStore ds = new SortingWFSDataStoreFactory().createDataStore(params);
      try {
        SimpleFeatureSource fs = ds.getFeatureSource(ds.getTypeNames()[0]);
        if (!withPage) {
          return new CountResult(WFSHitsCountUtil.getCount(fs, query), dispatcher.getFeatureRequests);
        }
        WFSHitsCountUtil.PageResponseCapture capture = WFSHitsCountUtil.capturePageResponses(fs);
        // Request a page through the HTTP client of the data store, as GeoTools does when reading features
        HTTPClient http = ((WFSDataStore) ds).getWfsClient().getHTTPClient();
        read(
            http,
            dispatcher.url + "?SERVICE=WFS&VERSION=2.0.0&REQUEST=GetFeature&TYPENAMES=" + TYPE_NAME
                + "&COUNT=1");
        // While parsing the features GeoTools requests the schema from the xsi:schemaLocation
        read(
            http,
            dispatcher.url + "?SERVICE=WFS&VERSION=2.0.0&REQUEST=DescribeFeatureType&TYPENAMES="
                + TYPE_NAME);
        return new CountResult(WFSHitsCountUtil.getCount(fs, query, capture), dispatcher.getFeatureRequests);
      } finally {
        ds.dispose();
      }
    }
  }

  private static void read(HTTPClient http, String url) throws IOException {
    HTTPResponse response = http.get(URI.create(url).toURL());
    try (InputStream in = response.getResponseStream()) {
      in.readAllBytes();
    } finally {
      response.dispose();
    }
  }

  @Test
  void hits_request_for_wfs_2_0_0() throws Exception {

    CountResult result = countWithServer(
        CAPABILITIES_2_0_0,
        resultType -> response("application/gml+xml; version=3.2", HITS_2_0_0.formatted("342")),
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
        resultType -> response("application/gml+xml; version=3.2", HITS_2_0_0.formatted("12")),
        "2.0.0",
        query);
    assertEquals(12, result.count());
    assertEquals(1, result.getFeatureRequests().size());
    String filter = result.getFeatureRequests().getFirst().get("FILTER");
    assertTrue(filter.contains("PropertyIsEqualTo") && filter.contains("A1"), filter);
  }

  @Test
  void number_matched_from_page() throws Exception {
    CountResult result = countWithServer(
        CAPABILITIES_2_0_0,
        resultType -> response(
            "application/gml+xml; version=3.2",
            ("HITS".equalsIgnoreCase(resultType) ? HITS_2_0_0 : PAGE_2_0_0).formatted("342")),
        "2.0.0",
        Query.ALL,
        true);
    assertEquals(342, result.count());
    // Only the request for the page, no hits request
    assertEquals(1, result.getFeatureRequests().size());
    assertNotEquals("HITS", result.getFeatureRequests().getFirst().getOrDefault("RESULTTYPE", ""));
  }

  @Test
  void hits_request_when_page_number_matched_unknown() throws Exception {
    CountResult result = countWithServer(
        CAPABILITIES_2_0_0,
        resultType -> response(
            "application/gml+xml; version=3.2",
            "HITS".equalsIgnoreCase(resultType)
                ? HITS_2_0_0.formatted("342")
                : PAGE_2_0_0.formatted("unknown")),
        "2.0.0",
        Query.ALL,
        true);
    assertEquals(342, result.count());
    assertEquals(2, result.getFeatureRequests().size());
    assertEquals(
        "HITS", result.getFeatureRequests().getLast().get("RESULTTYPE").toUpperCase(Locale.ROOT));
  }

  @Test
  void hits_request_unknown() throws Exception {

    CountResult result = countWithServer(
        CAPABILITIES_2_0_0,
        resultType -> response("application/gml+xml; version=3.2", HITS_2_0_0.formatted("unknown")),
        "2.0.0",
        Query.ALL);
    assertEquals(-1, result.count());
  }

  @Test
  void hits_request_exception_report() throws Exception {

    CountResult result = countWithServer(
        CAPABILITIES_2_0_0, resultType -> response("application/xml", EXCEPTION_REPORT), "2.0.0", Query.ALL);
    assertEquals(-1, result.count());
  }

  @Test
  void hits_request_http_error() throws Exception {

    CountResult result = countWithServer(
        CAPABILITIES_2_0_0,
        resultType -> new MockResponse.Builder().code(500).build(),
        "2.0.0",
        Query.ALL);
    assertEquals(-1, result.count());
  }

  @Test
  void no_extra_hits_request_for_wfs_1_1_0() throws Exception {

    CountResult result = countWithServer(
        CAPABILITIES_1_1_0,
        resultType -> response("text/xml; subtype=gml/3.1.1", HITS_1_1_0.formatted("342")),
        "1.1.0",
        Query.ALL);
    assertEquals(342, result.count());
    // Only the hits request GeoTools does itself
    assertEquals(1, result.getFeatureRequests().size());
  }
}
