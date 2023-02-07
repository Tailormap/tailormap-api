/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence.helper;

import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import nl.b3p.tailormap.api.config.HttpClientConfig;
import nl.b3p.tailormap.api.geotools.ResponseTeeingHTTPClient;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayer;
import nl.b3p.tailormap.api.persistence.json.ServiceCapabilitiesRequestGetFeatureInfo;
import nl.b3p.tailormap.api.persistence.json.ServiceCapabilitiesRequestGetMap;
import nl.b3p.tailormap.api.persistence.json.ServiceCaps;
import nl.b3p.tailormap.api.persistence.json.ServiceCapsCapabilities;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.data.ServiceInfo;
import org.geotools.data.ows.AbstractOpenWebService;
import org.geotools.data.ows.Capabilities;
import org.geotools.data.ows.OperationType;
import org.geotools.http.HTTPClient;
import org.geotools.http.HTTPClientFinder;
import org.geotools.ows.ServiceException;
import org.geotools.ows.wms.Layer;
import org.geotools.ows.wms.WMSCapabilities;
import org.geotools.ows.wms.WebMapServer;
import org.geotools.ows.wmts.WebMapTileServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
public class GeoServiceHelper {

  private static final Log log = LogFactory.getLog(GeoServiceHelper.class);
  private final HttpClientConfig httpClientConfig;

  @Autowired
  public GeoServiceHelper(HttpClientConfig httpClientConfig) {
    this.httpClientConfig = httpClientConfig;
  }

  public void loadServiceCapabilities(GeoService geoService) throws Exception {
    ResponseTeeingHTTPClient client = new ResponseTeeingHTTPClient(HTTPClientFinder.createClient());

    // TODO geoService.getAuthentication()
    client.setUser(null);
    client.setPassword(null);
    client.setReadTimeout(this.httpClientConfig.getTimeout());
    client.setConnectTimeout(this.httpClientConfig.getTimeout());
    client.setTryGzip(true);

    log.info(
        String.format(
            "Get capabilities for %s %s from URL %s",
            geoService.getProtocol(),
            geoService.getId() == null ? "(new)" : "id " + geoService.getId(),
            geoService.getUrl()));

    // TODO: micrometer met tags voor URL/id van service

    switch (geoService.getProtocol()) {
      case "wms":
        loadWMSCapabilities(geoService, client);
        break;
      case "wmts":
        loadWMTSCapabilities(geoService, client);
        break;
      default:
        throw new UnsupportedOperationException(
            "Unsupported geo service protocol: " + geoService.getProtocol());
    }

    if (log.isDebugEnabled()) {
      log.debug("Loaded service layers: " + geoService.getLayers());
    } else {
      log.info(
          "Loaded service layers: "
              + geoService.getLayers().stream()
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

    ServiceCaps caps = new ServiceCaps();
    geoService.setServiceCapabilities(caps);

    if (info != null) {
      caps.serviceInfo(
          new nl.b3p.tailormap.api.persistence.json.ServiceInfo()
              .keywords(info.getKeywords())
              .description(info.getDescription())
              .title(info.getTitle())
              .publisher(info.getPublisher())
              .schema(info.getSchema())
              .source(info.getSource()));

      geoService.setAdvertisedUrl(info.getSource().toString());
    }
  }

  private void setLayerList(GeoService geoService, List<? extends Layer> layers) {
    setLayerList(geoService, layers, (l, gsl) -> {});
  }

  private void setLayerList(
      GeoService geoService,
      List<? extends Layer> layers,
      BiConsumer<Layer, GeoServiceLayer> consumer) {

    for (Layer l : layers) {
      GeoServiceLayer geoServiceLayer =
          new GeoServiceLayer()
              .name(l.getName())
              .root(l.getParent() == null)
              .title(l.getTitle())
              .maxScale(
                  Double.isNaN(l.getScaleDenominatorMax()) ? null : l.getScaleDenominatorMax())
              .minScale(
                  Double.isNaN(l.getScaleDenominatorMin()) ? null : l.getScaleDenominatorMin())
              .virtual(l.getName() == null)
              .attribution(l.getAttribution() == null ? null : l.getAttribution().toString())
              .abstractText(l.get_abstract())
              .children(
                  l.getLayerChildren().stream().map(Layer::getName).collect(Collectors.toList()));
      if (consumer != null) {
        consumer.accept(l, geoServiceLayer);
      }
      geoService.getLayers().add(geoServiceLayer);
    }
  }

  void loadWMSCapabilities(GeoService geoService, ResponseTeeingHTTPClient client)
      throws Exception {
    WebMapServer wms = getWebMapServer(client, geoService.getUrl());

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
            new ServiceCapsCapabilities()
                .version(wmsCapabilities.getVersion())
                .updateSequence(wmsCapabilities.getUpdateSequence())
                .abstractText(wmsCapabilities.getService().get_abstract())
                .request(
                    new nl.b3p.tailormap.api.persistence.json.ServiceCapabilitiesRequest()
                        .getMap(
                            new ServiceCapabilitiesRequestGetMap()
                                .formats(Set.copyOf(getMap.getFormats())))
                        .getFeatureInfo(
                            getFeatureInfo == null
                                ? null
                                : new ServiceCapabilitiesRequestGetFeatureInfo()
                                    .formats(Set.copyOf(getFeatureInfo.getFormats())))
                        .describeLayer(
                            wms.getCapabilities().getRequest().getDescribeLayer() != null)));

    if (log.isDebugEnabled()) {
      log.debug(
          "Loaded capabilities, service capabilities: " + geoService.getServiceCapabilities());
    } else {
      log.info(
          String.format(
              "Loaded capabilities from \"%s\", title: \"%s\"",
              geoService.getUrl(),
              geoService.getServiceCapabilities() != null
                      && geoService.getServiceCapabilities().getServiceInfo() != null
                  ? geoService.getServiceCapabilities().getServiceInfo().getTitle()
                  : "(none)"));
    }

    setLayerList(geoService, wms.getCapabilities().getLayerList());
  }

  private WebMapServer getWebMapServer(HTTPClient client, String url)
      throws IOException, ServiceException {
    // Als we niet setupSpecifications() override krijgen we WMS 1.3.0 capabilities terug zonder
    // DescribeLayer
    return new WebMapServer(new URL(url), client) {
      /*  XXX does not work with https://wms.geonorge.no/skwms1/wms.adm_enheter2 (MapServer 7.4.2) */
      /*      @Override
      protected void setupSpecifications() {
        specs =
            new Specification[] {new WMS1_3_0(), new WMS1_1_1(), new WMS1_1_0(), new WMS1_0_0()};
      }*/
    };
  }

  void loadWMTSCapabilities(GeoService geoService, ResponseTeeingHTTPClient client)
      throws Exception {
    WebMapTileServer wmts = new WebMapTileServer(new URL(geoService.getUrl()), client);
    setServiceInfo(geoService, client, wmts);

    // TODO set capabilities if we need something from it

    setLayerList(geoService, wmts.getCapabilities().getLayerList());
  }
}
