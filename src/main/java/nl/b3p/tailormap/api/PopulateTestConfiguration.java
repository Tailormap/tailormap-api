/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api;

import javax.annotation.PostConstruct;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.BoundingBox;
import nl.b3p.tailormap.api.persistence.Configuration;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.json.AppContent;
import nl.b3p.tailormap.api.persistence.json.AppLayerRef;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.repository.ConfigurationRepository;
import nl.b3p.tailormap.api.repository.GeoServiceRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

@org.springframework.context.annotation.Configuration
public class PopulateTestConfiguration {
  private static final Log log = LogFactory.getLog(PopulateTestConfiguration.class);

  private GeoServiceRepository geoServiceRepository;
  private ApplicationRepository applicationRepository;
  private ConfigurationRepository configurationRepository;

  public PopulateTestConfiguration(
      GeoServiceRepository geoServiceRepository,
      ApplicationRepository applicationRepository,
      ConfigurationRepository configurationRepository) {
    this.geoServiceRepository = geoServiceRepository;
    this.applicationRepository = applicationRepository;
    this.configurationRepository = configurationRepository;
  }

  @PostConstruct
  public void populate() {
    GeoService test = new GeoService();
    test.setProtocol("wms");
    test.setUrl("https://snapshot.tailormap.nl/geoserver/wms");
    test.setTitle("Test GeoServer");

    geoServiceRepository.save(test);

    // geoServiceHelper.loadServiceCapabilities(test);

    Application app = new Application();
    app.setName("default");
    app.setCrs("EPSG:28992");
    app.setContentRoot(
        new AppContent()
            .addLayersItem(
                new AppLayerRef().serviceId(test.getId()).layerName("postgis:begroeidterreindeel"))
            .addLayersItem(new AppLayerRef().serviceId(test.getId()).layerName("sqlserver:wegdeel"))
            .addLayersItem(new AppLayerRef().serviceId(test.getId()).layerName("BGT")));
    app.setStartExtent(
        new BoundingBox()
            .setCrs("EPSG:28992")
            .setMinx(130011d)
            .setMiny(458031d)
            .setMaxx(132703d)
            .setMaxy(459995d));
    app.setMaxExtent(
        new BoundingBox()
            .setCrs("EPSG:28992")
            .setMinx(-285401d)
            .setMiny(22598d)
            .setMaxx(595401d)
            .setMaxy(903401d));
    applicationRepository.save(app);

    Configuration config = new Configuration();
    config.setKey(Configuration.DEFAULT_APP);
    config.setValue("default");
    configurationRepository.save(config);

    log.info(String.format("Test entity created with id %s%n", test.getId()));
  }
}
