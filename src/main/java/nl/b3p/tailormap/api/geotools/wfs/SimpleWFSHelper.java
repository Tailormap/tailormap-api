/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.geotools.wfs;

import static nl.b3p.tailormap.api.util.HttpProxyUtil.setHttpBasicAuthenticationHeader;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.xml.DomUtils;
import org.springframework.util.xml.SimpleNamespaceContext;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

/**
 * Lightweight WFS client helper with a fault-tolerant 'be liberal in what you accept' design - no
 * complete WFS XML mapping for capabilities and XML feature type schemas such as the heavyweight
 * GeoTools WFS DataStore.
 */
public class SimpleWFSHelper {

    private static HttpClient getDefaultHttpClient() {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    public static URI getWFSRequestURL(String wfsUrl, String request) {
        return getWFSRequestURL(wfsUrl, request, null);
    }

    public static URI getWFSRequestURL(
            String wfsUrl, String request, MultiValueMap<String, String> parameters) {
        return getWFSRequestURL(wfsUrl, request, parameters, "1.1.0");
    }

    public static URI getWFSRequestURL(
            String wfsUrl,
            String request,
            MultiValueMap<String, String> parameters,
            String version) {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("SERVICE", "WFS");
        params.add("REQUEST", request);
        params.add("VERSION", version);
        if (parameters != null) {
            params.addAll(parameters);
        }
        return UriComponentsBuilder.fromHttpUrl(wfsUrl).replaceQueryParams(params).build().toUri();
    }

    /**
     * Just get the list of supported output formats for the typename. If there are no specific
     * output formats for the type, the generally supported output formats are returned. Requests
     * WFS 1.1.0 but also handles WFS 2.0.0 responses.
     *
     * @param wfsUrl WFS URI
     * @param typeName Typename
     * @param username Optional username if required, may be null
     * @param password Optional password if required, may be null
     * @return List of output formats
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
        namespaceContext.bindNamespaceUri(
                "ows", "http://www.opengis.net/ows" + (wfs2 ? "/1.1" : ""));
        namespaceContext.bindNamespaceUri(
                "wfs", "http://www.opengis.net/wfs" + (wfs2 ? "/2.0" : ""));
        xPath.setNamespaceContext(namespaceContext);

        List<String> outputFormats = null;

        // Find all feature types and loop through to find the typeName. We can't use XPath with the
        // typeName parameter in it to find the exact FeatureType because escaping characters like '
        // in the typeName is not possible.
        NodeList featureTypes =
                (NodeList)
                        xPath.compile(
                                        "/wfs:WFS_Capabilities"
                                                + "/wfs:FeatureTypeList"
                                                + "/wfs:FeatureType")
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
                    outputFormats.add("fromFeatureType");
                }
                break;
            }
        }

        // No output formats found (or maybe not even the featureType...), return output formats
        // from OperationsMetadata
        if (outputFormats == null) {
            String xpathExpr =
                    "/wfs:WFS_Capabilities"
                            + "/ows:OperationsMetadata"
                            + "/ows:Operation[@name='GetFeature']"
                            + "/ows:Parameter[@name='outputFormat']"
                            + "//ows:Value";
            NodeList nodes =
                    (NodeList) xPath.compile(xpathExpr).evaluate(doc, XPathConstants.NODESET);

            outputFormats = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                outputFormats.add(DomUtils.getTextValue((Element) nodes.item(i)));
            }
            outputFormats.add("global");
        }

        outputFormats.add("wfsversion:" + doc.getDocumentElement().getAttribute("version"));

        return outputFormats;
    }
}
