/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.geotools.wfs;

import static nl.b3p.tailormap.api.util.HttpProxyUtil.setHttpBasicAuthenticationHeader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.geotools.http.HTTPClient;
import org.geotools.http.SimpleHttpClient;
import org.geotools.ows.ServiceException;
import org.geotools.ows.wms.LayerDescription;
import org.geotools.ows.wms.WMS1_1_1;
import org.geotools.ows.wms.WebMapServer;
import org.geotools.ows.wms.request.DescribeLayerRequest;
import org.geotools.ows.wms.response.DescribeLayerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.xml.DomUtils;
import org.springframework.util.xml.SimpleNamespaceContext;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Lightweight WFS client helper with a fault-tolerant 'be liberal in what you accept' design - no
 * complete WFS XML mapping for capabilities and XML feature type schemas such as the heavyweight
 * GeoTools WFS DataStore.
 */
public class SimpleWFSHelper {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public static final int TIMEOUT = 5000;

  private static HttpClient getDefaultHttpClient() {
    return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
  }

  public static URI getWFSRequestURL(String wfsUrl, String request) {
    return getWFSRequestURL(wfsUrl, request, null);
  }

  public static URI getWFSRequestURL(
      String wfsUrl, String request, MultiValueMap<String, String> parameters) {
    return getWFSRequestURL(wfsUrl, request, "1.1.0", parameters);
  }

  public static URI getWFSRequestURL(
      String wfsUrl, String request, String version, MultiValueMap<String, String> parameters) {
    return getOGCRequestURL(wfsUrl, "WFS", version, request, parameters);
  }

  public static URI getOGCRequestURL(
      String url,
      String service,
      String version,
      String request,
      MultiValueMap<String, String> parameters) {

    final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("SERVICE", service);
    params.add("VERSION", version);
    params.add("REQUEST", request);
    if (parameters != null) {
      // We need to encode the parameters manually because UriComponentsBuilder annoyingly does not
      // encode '+' as used in mime types for output formats, see
      // https://stackoverflow.com/questions/18138011
      parameters.replaceAll(
          (key, values) ->
              values.stream()
                  .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8))
                  .collect(Collectors.toList()));
      params.addAll(parameters);
    }
    return UriComponentsBuilder.fromHttpUrl(url).replaceQueryParams(params).build(true).toUri();
  }

  /**
   * Just get the list of supported output formats for the typename. If there are no specific output
   * formats for the type, the generally supported output formats are returned. Requests WFS 1.1.0
   * but also handles WFS 2.0.0 responses.
   */
  public static List<String> getOutputFormats(
      String wfsUrl, String typeName, String username, String password) throws Exception {
    return getOutputFormats(wfsUrl, typeName, username, password, getDefaultHttpClient());
  }

  public static List<String> getOutputFormats(
      String wfsUrl, String typeName, String username, String password, HttpClient httpClient)
      throws Exception {

    URI wfsGetCapabilities = getWFSRequestURL(wfsUrl, "GetCapabilities");

    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(wfsGetCapabilities);

    setHttpBasicAuthenticationHeader(requestBuilder, username, password);

    HttpResponse<InputStream> response =
        httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

    // Parse capabilities in DOM

    DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    documentBuilderFactory.setNamespaceAware(true);
    documentBuilderFactory.setExpandEntityReferences(false);
    documentBuilderFactory.setValidating(false);
    documentBuilderFactory.setXIncludeAware(false);
    documentBuilderFactory.setCoalescing(true);
    documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
    Document doc = documentBuilder.parse(response.body());

    // WFS 1.1.0 and WFS 2.0.0 use different namespaces, but the same local element names
    boolean wfs2 = "2.0.0".equals(doc.getDocumentElement().getAttribute("version"));
    XPath xPath = XPathFactory.newInstance().newXPath();
    SimpleNamespaceContext namespaceContext = new SimpleNamespaceContext();
    namespaceContext.bindNamespaceUri("ows", "http://www.opengis.net/ows" + (wfs2 ? "/1.1" : ""));
    namespaceContext.bindNamespaceUri("wfs", "http://www.opengis.net/wfs" + (wfs2 ? "/2.0" : ""));
    xPath.setNamespaceContext(namespaceContext);

    List<String> outputFormats = null;

    // Find all feature types and loop through to find the typeName. We can't use XPath with the
    // typeName parameter in it to find the exact FeatureType because escaping characters like '
    // in the typeName is not possible.
    NodeList featureTypes =
        (NodeList)
            xPath
                .compile("/wfs:WFS_Capabilities" + "/wfs:FeatureTypeList" + "/wfs:FeatureType")
                .evaluate(doc, XPathConstants.NODESET);

    for (int i = 0; i < featureTypes.getLength(); i++) {
      Element n = (Element) featureTypes.item(i);
      Element name = DomUtils.getChildElementByTagName(n, "Name");
      if (name != null && typeName.equals(DomUtils.getTextValue(name))) {
        Element formatsNode = DomUtils.getChildElementByTagName(n, "OutputFormats");
        if (formatsNode != null) {
          outputFormats =
              DomUtils.getChildElementsByTagName(formatsNode, "Format").stream()
                  .map(DomUtils::getTextValue)
                  .collect(Collectors.toList());
        }
        break;
      }
    }

    // No output formats found (or maybe not even the featureType...), return output formats from
    // OperationsMetadata
    if (outputFormats == null) {
      String xpathExpr =
          "/wfs:WFS_Capabilities"
              + "/ows:OperationsMetadata"
              + "/ows:Operation[@name='GetFeature']"
              + "/ows:Parameter[@name='outputFormat']"
              + "//ows:Value";
      NodeList nodes = (NodeList) xPath.compile(xpathExpr).evaluate(doc, XPathConstants.NODESET);

      outputFormats = new ArrayList<>();
      for (int i = 0; i < nodes.getLength(); i++) {
        outputFormats.add(DomUtils.getTextValue((Element) nodes.item(i)));
      }
    }

    return outputFormats;
  }

  public static WebMapServer getWebMapServer(String url, String username, String password)
      throws IOException, ServiceException {
    HTTPClient client = new SimpleHttpClient();
    client.setUser(username);
    client.setPassword(password);
    client.setConnectTimeout(TIMEOUT);
    client.setReadTimeout(TIMEOUT);
    return new WebMapServer(new URL(url), client);
  }

  public static SimpleWFSLayerDescription describeWMSLayer(
      String url, String username, String password, String layerName) {
    return describeWMSLayers(url, username, password, List.of(layerName)).get(layerName);
  }

  public static Map<String, SimpleWFSLayerDescription> describeWMSLayers(
      String url, String username, String password, List<String> layers) {
    try {
      WebMapServer wms = getWebMapServer(url, username, password);
      // Directly create WMS 1.1.1 request. Creating it from WebMapServer errors with GeoServer
      // about unsupported request in capabilities unless we override WebMapServer to set up
      // specifications.
      DescribeLayerRequest describeLayerRequest =
          new WMS1_1_1().createDescribeLayerRequest(new URL(url));
      // XXX Otherwise GeoTools will send VERSION=1.1.0...
      describeLayerRequest.setProperty("VERSION", "1.1.1");
      describeLayerRequest.setLayers(String.join(",", layers));
      // GeoTools will throw a ClassCastException when a WMS ServiceException is returned
      DescribeLayerResponse describeLayerResponse = wms.issueRequest(describeLayerRequest);

      Map<String, SimpleWFSLayerDescription> descriptions = new HashMap<>();
      for (LayerDescription ld : describeLayerResponse.getLayerDescs()) {
        String wfsUrl = getWfsUrl(ld, wms);

        if (wfsUrl != null && ld.getQueries() != null && ld.getQueries().length != 0) {
          descriptions.put(ld.getName(), new SimpleWFSLayerDescription(wfsUrl, ld.getQueries()));
        }
      }
      return Collections.unmodifiableMap(descriptions);
    } catch (Exception e) {
      String msg =
          String.format(
              "Error in DescribeLayer request to WMS \"%s\": %s: %s",
              url, e.getClass(), e.getMessage());
      if (logger.isTraceEnabled()) {
        logger.trace(msg, e);
      } else {
        logger.debug(msg + ". Set log level to TRACE for stacktrace.");
      }
    }

    return Collections.emptyMap();
  }

  private static String getWfsUrl(LayerDescription ld, WebMapServer wms) {
    String wfsUrl = (ld.getWfs() != null) ? ld.getWfs().toString() : null;
    if (wfsUrl == null && "WFS".equalsIgnoreCase(ld.getOwsType())) {
      wfsUrl = ld.getOwsURL().toString();
    }
    // OGC 02-070 Annex B says the wfs/owsURL attributed are not required but implied. Some
    // Deegree instance encountered has all attributes empty, and apparently the meaning is that
    // the WFS URL is the same as the WMS URL (not explicitly defined in the spec).
    if (wfsUrl == null) {
      wfsUrl = wms.getInfo().getSource().toString();
    }
    return wfsUrl;
  }
}
