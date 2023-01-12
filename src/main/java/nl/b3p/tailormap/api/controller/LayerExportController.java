/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.model.LayerExportCapabilities;
import nl.b3p.tailormap.api.repository.LayerRepository;
import nl.tailormap.viewer.config.app.Application;
import nl.tailormap.viewer.config.app.ApplicationLayer;
import nl.tailormap.viewer.config.services.FeatureSource;
import nl.tailormap.viewer.config.services.GeoService;
import nl.tailormap.viewer.config.services.Layer;
import nl.tailormap.viewer.config.services.SimpleFeatureType;
import nl.tailormap.viewer.config.services.WFSFeatureSource;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.xml.DomUtils;
import org.springframework.util.xml.SimpleNamespaceContext;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

@AppRestController
@Validated
@RequestMapping(path = "/app/{appId}/layer/{appLayerId}/export/")
public class LayerExportController {

    private final LayerRepository layerRepository;

    public LayerExportController(LayerRepository layerRepository) {
        this.layerRepository = layerRepository;
    }

    @GetMapping(path = "capabilities")
    public ResponseEntity<Serializable> capabilities(
            @ModelAttribute Application application,
            @ModelAttribute ApplicationLayer applicationLayer)
            throws IOException, InterruptedException, ParserConfigurationException, SAXException,
                    XPathExpressionException {
        LayerExportCapabilities capabilities = new LayerExportCapabilities();
        capabilities.setExportable(false);
        capabilities.setOutputFormats(Collections.emptyList());

        // TODO move to injectable service

        final GeoService service = applicationLayer.getService();
        final Layer serviceLayer =
                this.layerRepository.getByServiceAndName(service, applicationLayer.getLayerName());

        SimpleFeatureType featureType = serviceLayer.getFeatureType();

        if (featureType != null) {
            FeatureSource featureSource = featureType.getFeatureSource();

            if (featureSource instanceof WFSFeatureSource) {

                final String wfsUrl = featureSource.getUrl();
                final String typeName = featureType.getTypeName();

                final MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
                params.add("SERVICE", "WFS");
                params.add("REQUEST", "GetCapabilities");
                params.add("VERSION", "1.1.0");
                URI uri =
                        UriComponentsBuilder.fromHttpUrl(wfsUrl)
                                .replaceQueryParams(params)
                                .build()
                                .toUri();

                final HttpClient.Builder builder =
                        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL);
                final HttpClient httpClient = builder.build();
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(uri);

                if (featureSource.getUsername() != null && featureSource.getPassword() != null) {
                    String toEncode =
                            featureSource.getUsername() + ":" + featureSource.getPassword();
                    requestBuilder.header(
                            "Authorization",
                            "Basic "
                                    + Base64.getEncoder()
                                            .encodeToString(
                                                    toEncode.getBytes(StandardCharsets.UTF_8)));
                }

                HttpResponse<InputStream> response =
                        httpClient.send(
                                requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

                DocumentBuilderFactory documentBuilderFactory =
                        DocumentBuilderFactory.newInstance();
                documentBuilderFactory.setNamespaceAware(true);
                documentBuilderFactory.setExpandEntityReferences(false);
                documentBuilderFactory.setValidating(false);
                documentBuilderFactory.setXIncludeAware(false);
                documentBuilderFactory.setCoalescing(true);
                documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
                Document doc = documentBuilder.parse(response.body());

                boolean wfs2 = "2.0.0".equals(doc.getDocumentElement().getAttribute("version"));
                XPath xPath = XPathFactory.newInstance().newXPath();
                SimpleNamespaceContext namespaceContext = new SimpleNamespaceContext();
                namespaceContext.bindNamespaceUri(
                        "ows", "http://www.opengis.net/ows" + (wfs2 ? "/1.1" : ""));
                namespaceContext.bindNamespaceUri(
                        "wfs", "http://www.opengis.net/wfs" + (wfs2 ? "/2.0" : ""));
                xPath.setNamespaceContext(namespaceContext);

                List<String> outputFormats = null;

                // We can't use XPath to find the exact FeatureType by name because escaping
                // characters in the typeName is not possible
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
                                    DomUtils.getChildElementsByTagName(formatsNode, "Format")
                                            .stream()
                                            .map(DomUtils::getTextValue)
                                            .collect(Collectors.toList());
                            outputFormats.add("fromFeatureType");
                        }
                        break;
                    }
                }

                if (outputFormats == null) {
                    NodeList nodes =
                            (NodeList)
                                    xPath.compile(
                                                    "/wfs:WFS_Capabilities"
                                                            + "/ows:OperationsMetadata"
                                                            + "/ows:Operation[@name='GetFeature']"
                                                            + "/ows:Parameter[@name='outputFormat']"
                                                            + "//ows:Value")
                                            .evaluate(doc, XPathConstants.NODESET);

                    outputFormats = new ArrayList<>();
                    for (int i = 0; i < nodes.getLength(); i++) {
                        outputFormats.add(DomUtils.getTextValue((Element) nodes.item(i)));
                    }
                    outputFormats.add("global");
                }

                outputFormats.add("wfsversion:" + doc.getDocumentElement().getAttribute("version"));

                capabilities.setExportable(true);
                capabilities.setOutputFormats(outputFormats);
            }
        }

        return ResponseEntity.status(HttpStatus.OK).body(capabilities);
    }
}
