/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.wfs;

import static org.tailormap.api.util.HttpProxyUtil.setHttpBasicAuthenticationHeader;

import java.io.InputStream;
import java.io.Reader;
import java.lang.invoke.MethodHandles;
import java.net.URI;
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
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
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
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

public class SimpleWFSHelper {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public static final int TIMEOUT = 5000;

  public static final String DEFAULT_WFS_VERSION = "1.1.0";

  private static HttpClient getDefaultHttpClient() {
    return HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
  }

  public static URI getWFSRequestURL(String wfsUrl, String request) {
    return getWFSRequestURL(wfsUrl, request, null);
  }

  public static URI getWFSRequestURL(String wfsUrl, String request, MultiValueMap<String, String> parameters) {
    return getWFSRequestURL(wfsUrl, request, DEFAULT_WFS_VERSION, parameters);
  }

  public static URI getWFSRequestURL(
      String wfsUrl, String request, String version, MultiValueMap<String, String> parameters) {
    return getOGCRequestURL(wfsUrl, "WFS", version, request, parameters);
  }

  public static URI getOGCRequestURL(
      String url, String service, String version, String request, MultiValueMap<String, String> parameters) {

    final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("SERVICE", service);
    params.add("VERSION", version);
    params.add("REQUEST", request);
    if (parameters != null) {
      // We need to encode the parameters manually because UriComponentsBuilder annoyingly does not
      // encode '+' as used in mime types for output formats, see
      // https://stackoverflow.com/questions/18138011
      parameters.replaceAll((unusedKey, values) -> values.stream()
          .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8))
          .collect(Collectors.toList()));
      params.addAll(parameters);
    }
    return UriComponentsBuilder.fromUriString(url)
        .replaceQueryParams(params)
        .build(true)
        .toUri();
  }

  /**
   * Get a list of GetFeature output formats for a WFS feature type.
   *
   * <p>If there are no specific output formats for the type, the generally supported output formats are returned.
   * Requests WFS 1.1.0 but also handles WFS 2.0.0 responses.
   *
   * <p>Uses a 'lightweight' WFS implementation parsing only the XML WFS capabilities to extract the output formats
   * using XPath, instead of using a heavyweight GeoTools WFS DataStore which is much slower.
   */
  public static List<String> getOutputFormats(String wfsUrl, String typeName, String username, String password)
      throws Exception {
    return getOutputFormats(wfsUrl, typeName, username, password, getDefaultHttpClient());
  }

  public static List<String> getOutputFormats(
      String wfsUrl, String typeName, String username, String password, HttpClient httpClient) throws Exception {

    URI wfsGetCapabilities = getWFSRequestURL(wfsUrl, "GetCapabilities");

    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(wfsGetCapabilities);

    setHttpBasicAuthenticationHeader(requestBuilder, username, password);

    HttpResponse<InputStream> response =
        httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

    Document doc = parseSecureXml(response.body());

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
        (NodeList) xPath.compile("/wfs:WFS_Capabilities" + "/wfs:FeatureTypeList" + "/wfs:FeatureType")
            .evaluate(doc, XPathConstants.NODESET);

    for (int i = 0; i < featureTypes.getLength(); i++) {
      Element n = (Element) featureTypes.item(i);
      Element name = DomUtils.getChildElementByTagName(n, "Name");
      if (name != null && typeName.equals(DomUtils.getTextValue(name))) {
        Element formatsNode = DomUtils.getChildElementByTagName(n, "OutputFormats");
        if (formatsNode != null) {
          outputFormats = DomUtils.getChildElementsByTagName(formatsNode, "Format").stream()
              .map(DomUtils::getTextValue)
              .collect(Collectors.toList());
        }
        break;
      }
    }

    // No output formats found (or maybe not even the featureType...), return output formats from
    // OperationsMetadata
    if (outputFormats == null) {
      String xpathExpr = "/wfs:WFS_Capabilities"
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

  public static SimpleWFSLayerDescription describeWMSLayer(
      String url, String username, String password, String layerName) {
    return describeWMSLayers(url, username, password, List.of(layerName)).get(layerName);
  }

  public static Map<String, SimpleWFSLayerDescription> describeWMSLayers(
      String url, String username, String password, List<String> layers) {
    try (HttpClient httpClient = getDefaultHttpClient(); ) {
      URI describeLayerUri = getDescribeLayerRequestUrl(url, layers);
      HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(describeLayerUri);
      setHttpBasicAuthenticationHeader(requestBuilder, username, password);

      HttpResponse<InputStream> response =
          httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
      Document document = parseXmlAllowingDocType(response.body());
      Map<String, SimpleWFSLayerDescription> descriptions = parseDescribeLayerResponse(document, url);
      return Collections.unmodifiableMap(descriptions);
    } catch (Exception e) {
      String msg =
          "Error in DescribeLayer request to WMS \"%s\": %s: %s".formatted(url, e.getClass(), e.getMessage());
      if (logger.isTraceEnabled()) {
        logger.trace(msg, e);
      } else {
        logger.debug("{}. Set log level to TRACE for stacktrace.", msg);
      }
    }
    return Map.of();
  }

  private static URI getDescribeLayerRequestUrl(String url, List<String> layers) {
    MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
    parameters.add("LAYERS", String.join(",", layers));
    return getOGCRequestURL(url, "WMS", "1.1.1", "DescribeLayer", parameters);
  }

  private static Document parseSecureXml(InputStream inputStream) throws Exception {
    return getDocumentBuilder().parse(inputStream);
  }

  private static DocumentBuilder getDocumentBuilder() throws ParserConfigurationException {
    DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    documentBuilderFactory.setNamespaceAware(true);
    documentBuilderFactory.setExpandEntityReferences(false);
    documentBuilderFactory.setValidating(false);
    documentBuilderFactory.setXIncludeAware(false);
    documentBuilderFactory.setCoalescing(true);
    documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    return documentBuilderFactory.newDocumentBuilder();
  }

  private static Document parseXmlAllowingDocType(InputStream inputStream) throws Exception {
    DocumentBuilder documentBuilder = getDocumentBuilder();
    EntityResolver entityResolver = (publicId, systemId) -> new InputSource(Reader.of(""));
    documentBuilder.setEntityResolver(entityResolver);

    return documentBuilder.parse(inputStream);
  }

  private static Map<String, SimpleWFSLayerDescription> parseDescribeLayerResponse(
      Document document, String fallbackUrl) {
    Element root = document.getDocumentElement();
    if (root == null) {
      return Map.of();
    }

    NodeList layerDescriptions = root.getElementsByTagNameNS("*", "LayerDescription");
    Map<String, SimpleWFSLayerDescription> descriptions = new HashMap<>();

    for (int i = 0; i < layerDescriptions.getLength(); i++) {
      Element layerDescription = (Element) layerDescriptions.item(i);

      String layerName = layerDescription.getAttribute("name");
      String wfsUrl = getDescribeLayerWfsUrl(layerDescription, fallbackUrl);

      List<String> typeNames = new ArrayList<>();
      NodeList queryNodes = layerDescription.getElementsByTagNameNS("*", "Query");
      for (int j = 0; j < queryNodes.getLength(); j++) {
        Element query = (Element) queryNodes.item(j);
        String typeName = query.getAttribute("typeName");
        if (!typeName.isBlank()) {
          typeNames.add(typeName);
        }
      }

      if (!layerName.isBlank() && wfsUrl != null && !typeNames.isEmpty()) {
        descriptions.put(layerName, new SimpleWFSLayerDescription(wfsUrl, List.copyOf(typeNames)));
      }
    }

    return descriptions;
  }

  private static String getDescribeLayerWfsUrl(Element layerDescription, String fallbackUrl) {
    String wfsUrl = layerDescription.getAttribute("wfs");
    if (!wfsUrl.isBlank()) {
      return wfsUrl;
    }

    String owsType = layerDescription.getAttribute("owsType");
    String owsUrl = layerDescription.getAttribute("owsURL");
    if ("WFS".equalsIgnoreCase(owsType) && !owsUrl.isBlank()) {
      return owsUrl;
    }

    return fallbackUrl;
  }
}
