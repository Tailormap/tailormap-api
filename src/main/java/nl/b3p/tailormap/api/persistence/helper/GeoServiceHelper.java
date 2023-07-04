/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence.helper;

import static nl.b3p.tailormap.api.persistence.TMFeatureSource.Protocol.WFS;
import static nl.b3p.tailormap.api.persistence.json.GeoServiceProtocol.WMS;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import nl.b3p.tailormap.api.configuration.TailormapConfig;
import nl.b3p.tailormap.api.geotools.ResponseTeeingHTTPClient;
import nl.b3p.tailormap.api.geotools.WMSServiceExceptionUtil;
import nl.b3p.tailormap.api.geotools.featuresources.WFSFeatureSourceHelper;
import nl.b3p.tailormap.api.geotools.wfs.SimpleWFSHelper;
import nl.b3p.tailormap.api.geotools.wfs.SimpleWFSLayerDescription;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.TMFeatureSource;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayer;
import nl.b3p.tailormap.api.persistence.json.ServiceAuthentication;
import nl.b3p.tailormap.api.persistence.json.TMServiceCapabilitiesRequest;
import nl.b3p.tailormap.api.persistence.json.TMServiceCapabilitiesRequestGetFeatureInfo;
import nl.b3p.tailormap.api.persistence.json.TMServiceCapabilitiesRequestGetMap;
import nl.b3p.tailormap.api.persistence.json.TMServiceCaps;
import nl.b3p.tailormap.api.persistence.json.TMServiceCapsCapabilities;
import nl.b3p.tailormap.api.persistence.json.TMServiceInfo;
import nl.b3p.tailormap.api.persistence.json.WMSStyle;
import nl.b3p.tailormap.api.repository.FeatureSourceRepository;
import org.apache.commons.lang3.StringUtils;
import org.geotools.data.ServiceInfo;
import org.geotools.data.ows.AbstractOpenWebService;
import org.geotools.data.ows.Capabilities;
import org.geotools.data.ows.OperationType;
import org.geotools.http.HTTPClientFinder;
import org.geotools.ows.wms.Layer;
import org.geotools.ows.wms.WMSCapabilities;
import org.geotools.ows.wms.WebMapServer;
import org.geotools.ows.wmts.WebMapTileServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
public class GeoServiceHelper {

  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final TailormapConfig tailormapConfig;
  private final FeatureSourceRepository featureSourceRepository;

  @Autowired
  public GeoServiceHelper(
      TailormapConfig tailormapConfig, FeatureSourceRepository featureSourceRepository) {
    this.tailormapConfig = tailormapConfig;
    this.featureSourceRepository = featureSourceRepository;
  }

  public nl.b3p.tailormap.api.viewer.model.Service.ServerTypeEnum guessServerTypeFromUrl(
      String url) {

    if (StringUtils.isBlank(url)) {
      return nl.b3p.tailormap.api.viewer.model.Service.ServerTypeEnum.GENERIC;
    }
    if (url.contains("/arcgis/")) {
      return nl.b3p.tailormap.api.viewer.model.Service.ServerTypeEnum.GENERIC;
    }
    if (url.contains("/geoserver/")) {
      return nl.b3p.tailormap.api.viewer.model.Service.ServerTypeEnum.GEOSERVER;
    }
    if (url.contains("/mapserv")) { // /cgi-bin/mapserv, /cgi-bin/mapserv.cgi, /cgi-bin/mapserv.fcgi
      return nl.b3p.tailormap.api.viewer.model.Service.ServerTypeEnum.MAPSERVER;
    }
    return nl.b3p.tailormap.api.viewer.model.Service.ServerTypeEnum.GENERIC;
  }

  public void loadServiceCapabilities(GeoService geoService) throws Exception {
    ResponseTeeingHTTPClient client = new ResponseTeeingHTTPClient(HTTPClientFinder.createClient());

    ServiceAuthentication auth = geoService.getAuthentication();
    if (auth != null && auth.getMethod() == ServiceAuthentication.MethodEnum.PASSWORD) {
      client.setUser(auth.getUsername());
      client.setPassword(auth.getPassword());
    }

    client.setReadTimeout(this.tailormapConfig.getTimeout());
    client.setConnectTimeout(this.tailormapConfig.getTimeout());
    client.setTryGzip(true);

    logger.info(
        "Get capabilities for {} {} from URL {}",
        geoService.getProtocol(),
        geoService.getId() == null ? "(new)" : "id " + geoService.getId(),
        geoService.getUrl());

    // TODO: micrometer met tags voor URL/id van service

    switch (geoService.getProtocol()) {
      case WMS:
        loadWMSCapabilities(geoService, client);
        break;
      case WMTS:
        loadWMTSCapabilities(geoService, client);
        break;
      default:
        throw new UnsupportedOperationException(
            "Unsupported geo service protocol: " + geoService.getProtocol());
    }

    if (geoService.getTitle() == null) {
      geoService.setTitle(
          Optional.ofNullable(geoService.getServiceCapabilities())
              .map(TMServiceCaps::getServiceInfo)
              .map(TMServiceInfo::getTitle)
              .orElse(null));
    }

    if (logger.isDebugEnabled()) {
      logger.debug("Loaded service layers: {}", geoService.getLayers());
    } else {
      logger.info(
          "Loaded service layers: {}",
          geoService.getLayers().stream()
              .filter(Predicate.not(GeoServiceLayer::getVirtual))
              .map(GeoServiceLayer::getName)
              .collect(Collectors.toList()));
    }
  }

  private void setServiceInfo(
      GeoService geoService,
      ResponseTeeingHTTPClient client,
      AbstractOpenWebService<? extends Capabilities, Layer> ows) {
    geoService.setCapabilities(client.getResponseCopy());
    geoService.setCapabilitiesContentType(MediaType.APPLICATION_XML_VALUE);
    // geoService.setCapabilitiesContentType(client.getResponse().getContentType());
    geoService.setCapabilitiesFetched(Instant.now());

    ServiceInfo info = ows.getInfo();

    TMServiceCaps caps = new TMServiceCaps();
    geoService.setServiceCapabilities(caps);

    if (info != null) {
      if (StringUtils.isBlank(geoService.getTitle())) {
        geoService.setTitle(info.getTitle());
      }

      caps.serviceInfo(
          new TMServiceInfo()
              .keywords(info.getKeywords())
              .description(info.getDescription())
              .title(info.getTitle())
              .publisher(info.getPublisher())
              .schema(info.getSchema())
              .source(info.getSource()));

      geoService.setAdvertisedUrl(info.getSource().toString());
    } else if (ows.getCapabilities() != null && ows.getCapabilities().getService() != null) {
      org.geotools.data.ows.Service service = ows.getCapabilities().getService();

      if (StringUtils.isBlank(geoService.getTitle())) {
        geoService.setTitle(service.getTitle());
      }
      caps.setServiceInfo(
          new TMServiceInfo().keywords(Set.copyOf(List.of(service.getKeywordList()))));
    }
  }

  private void setLayerList(GeoService geoService, List<? extends Layer> layers) {
    setLayerList(geoService, layers, (l, gsl) -> {});
  }

  private void setLayerList(
      GeoService geoService,
      List<? extends Layer> layers,
      BiConsumer<Layer, GeoServiceLayer> consumer) {
    geoService.setLayers(new ArrayList<>());

    for (Layer l : layers) {
      GeoServiceLayer geoServiceLayer =
          new GeoServiceLayer()
              .id(String.valueOf(layers.indexOf(l)))
              .name(l.getName())
              .root(l.getParent() == null)
              .title(l.getTitle())
              .maxScale(
                  Double.isNaN(l.getScaleDenominatorMax()) ? null : l.getScaleDenominatorMax())
              .minScale(
                  Double.isNaN(l.getScaleDenominatorMin()) ? null : l.getScaleDenominatorMin())
              .virtual(l.getName() == null)
              .crs(l.getSrs())
              .latLonBoundingBox(GeoToolsHelper.boundsFromCRSEnvelope(l.getLatLonBoundingBox()))
              .styles(
                  l.getStyles().stream()
                      .map(
                          gtStyle -> {
                            WMSStyle style =
                                new WMSStyle()
                                    .name(gtStyle.getName())
                                    .title(
                                        Optional.ofNullable(gtStyle.getTitle())
                                            .map(Objects::toString)
                                            .orElse(null))
                                    .abstractText(
                                        Optional.ofNullable(gtStyle.getAbstract())
                                            .map(Objects::toString)
                                            .orElse(null));
                            try {
                              List legendURLs = gtStyle.getLegendURLs();
                              if (legendURLs != null && !legendURLs.isEmpty()) {
                                style.legendURL(new URI((String) legendURLs.get(0)));
                              }
                            } catch (URISyntaxException ignored) {
                              // Don't care
                            }
                            return style;
                          })
                      .collect(Collectors.toList()))
              .queryable(l.isQueryable())
              .abstractText(l.get_abstract())
              .children(
                  l.getLayerChildren().stream()
                      .map(layers::indexOf)
                      .map(String::valueOf)
                      .collect(Collectors.toList()));

      if (consumer != null) {
        consumer.accept(l, geoServiceLayer);
      }
      geoService.getLayers().add(geoServiceLayer);
    }
  }

  void loadWMSCapabilities(GeoService geoService, ResponseTeeingHTTPClient client)
      throws Exception {
    WebMapServer wms = null;
    try {
      wms = new WebMapServer(new URL(geoService.getUrl()), client);
    } catch(ClassCastException | IllegalStateException e) {
      // The gt-wms module tries to cast the XML unmarshalling result expecting capabilities, but a
      // WMS 1.0.0/1.1.0 ServiceException may have been unmarshalled which leads to a ClassCastException.

      // A WMS 1.3.0 ServiceExceptionReport leads to an IllegalStateException because of a call to Throwable.initCause() on a SAXException that already has a cause.

      // In these cases, try to extract a message from the HTTP response

      String contentType = client.getResponse().getContentType();
      if (contentType != null && contentType.contains("text/xml")) {
        String wmsException =
            WMSServiceExceptionUtil.tryGetServiceExceptionMessage(client.getResponseCopy());
        throw new Exception(
            "Error loading WMS capabilities: " + wmsException != null
                ? wmsException
                : new String(client.getResponseCopy(), StandardCharsets.UTF_8));
      } else {
        throw e;
      }
    }

    OperationType getMap = wms.getCapabilities().getRequest().getGetMap();
    OperationType getFeatureInfo = wms.getCapabilities().getRequest().getGetFeatureInfo();

    if (getMap == null) {
      throw new Exception("Service does not support GetMap");
    }

    setServiceInfo(geoService, client, wms);

    WMSCapabilities wmsCapabilities = wms.getCapabilities();

    // TODO Jackson annotations op GeoTools classes of iets anders slims?

    geoService
        .getServiceCapabilities()
        .capabilities(
            new TMServiceCapsCapabilities()
                .version(wmsCapabilities.getVersion())
                .updateSequence(wmsCapabilities.getUpdateSequence())
                .abstractText(wmsCapabilities.getService().get_abstract())
                .request(
                    new TMServiceCapabilitiesRequest()
                        .getMap(
                            new TMServiceCapabilitiesRequestGetMap()
                                .formats(Set.copyOf(getMap.getFormats())))
                        .getFeatureInfo(
                            getFeatureInfo == null
                                ? null
                                : new TMServiceCapabilitiesRequestGetFeatureInfo()
                                    .formats(Set.copyOf(getFeatureInfo.getFormats())))
                        .describeLayer(
                            wms.getCapabilities().getRequest().getDescribeLayer() != null)));

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Loaded capabilities, service capabilities: {}", geoService.getServiceCapabilities());
    } else {
      logger.info(
          "Loaded capabilities from \"{}\", title: \"{}\"",
          geoService.getUrl(),
          geoService.getServiceCapabilities() != null
                  && geoService.getServiceCapabilities().getServiceInfo() != null
              ? geoService.getServiceCapabilities().getServiceInfo().getTitle()
              : "(none)");
    }

    setLayerList(geoService, wms.getCapabilities().getLayerList());
  }

  void loadWMTSCapabilities(GeoService geoService, ResponseTeeingHTTPClient client)
      throws Exception {
    WebMapTileServer wmts = new WebMapTileServer(new URL(geoService.getUrl()), client);
    setServiceInfo(geoService, client, wmts);

    // TODO set capabilities if we need something from it

    setLayerList(geoService, wmts.getCapabilities().getLayerList());
  }

  public Map<String, SimpleWFSLayerDescription> findRelatedWFS(GeoService geoService) {
    // TODO: report back progress

    if (CollectionUtils.isEmpty(geoService.getLayers())) {
      return Collections.emptyMap();
    }

    // Do one DescribeLayer request for all layers in a WMS. This is faster than one request per
    // layer, but when one layer has an error this prevents describing valid layers. But that's a
    // problem with the WMS / GeoServer.
    // For now at least ignore layers with space in the name because GeoServer chokes out invalid
    // XML for those.

    List<String> layers =
        geoService.getLayers().stream()
            .filter(l -> !l.getVirtual())
            .map(GeoServiceLayer::getName)
            .filter(
                n -> {
                  // filter out white-space (non-greedy regex)
                  boolean noWhitespace = !n.contains("(.*?)\\s(.*?)");
                  if (!noWhitespace) {
                    logger.warn(
                        String.format(
                            "Not doing WFS DescribeLayer request for layer name with space: \"%s\" of WMS %s",
                            n, geoService.getUrl()));
                  }
                  return noWhitespace;
                })
            .collect(Collectors.toList());

    // TODO: add authentication
    Map<String, SimpleWFSLayerDescription> descriptions =
        SimpleWFSHelper.describeWMSLayers(geoService.getUrl(), null, null, layers);

    for (Map.Entry<String, SimpleWFSLayerDescription> entry : descriptions.entrySet()) {
      String layerName = entry.getKey();
      SimpleWFSLayerDescription description = entry.getValue();
      if (description.getTypeNames().length == 1
          && layerName.equals(description.getFirstTypeName())) {
        logger.info(
            String.format(
                "layer \"%s\" linked to feature type with same name of WFS %s",
                layerName, description.getWfsUrl()));
      } else {
        logger.info(
            String.format(
                "layer \"%s\" -> feature type(s) %s of WFS %s",
                layerName, Arrays.toString(description.getTypeNames()), description.getWfsUrl()));
      }
    }
    return descriptions;
  }

  public void findAndSaveRelatedWFS(GeoService geoService) {
    if (geoService.getProtocol() != WMS) {
      throw new IllegalArgumentException();
    }

    // TODO: report back progress

    Map<String, SimpleWFSLayerDescription> wfsByLayer = this.findRelatedWFS(geoService);

    wfsByLayer.values().stream()
        .map(SimpleWFSLayerDescription::getWfsUrl)
        .distinct()
        .forEach(
            url -> {
              TMFeatureSource fs = featureSourceRepository.findByUrl(url);
              if (fs == null) {
                fs =
                    new TMFeatureSource()
                        .setProtocol(WFS)
                        .setUrl(url)
                        .setTitle("WFS for " + geoService.getTitle())
                        .setLinkedService(geoService);
                try {
                  new WFSFeatureSourceHelper().loadCapabilities(fs, tailormapConfig.getTimeout());
                } catch (IOException e) {
                  String msg =
                      String.format(
                          "Error loading WFS from URL %s: %s: %s",
                          url, e.getClass(), e.getMessage());
                  if (logger.isTraceEnabled()) {
                    logger.error(msg, e);
                  } else {
                    logger.error(msg);
                  }
                }
                featureSourceRepository.save(fs);
              }
            });
  }
}
