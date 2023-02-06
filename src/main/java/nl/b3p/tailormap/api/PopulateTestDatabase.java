/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api;

import javax.annotation.PostConstruct;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.Configuration;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.json.AppContent;
import nl.b3p.tailormap.api.persistence.json.AppLayerRef;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.repository.ConfigurationRepository;
import nl.b3p.tailormap.api.repository.GeoServiceRepository;
import nl.b3p.tailormap.api.viewer.model.Bounds;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.annotation.Profile;

@org.springframework.context.annotation.Configuration
@Profile("!test")
public class PopulateTestDatabase {
  private static final Log log = LogFactory.getLog(PopulateTestDatabase.class);

  private final GeoServiceRepository geoServiceRepository;
  private final ApplicationRepository applicationRepository;
  private final ConfigurationRepository configurationRepository;

  public PopulateTestDatabase(
      GeoServiceRepository geoServiceRepository,
      ApplicationRepository applicationRepository,
      ConfigurationRepository configurationRepository) {
    this.geoServiceRepository = geoServiceRepository;
    this.applicationRepository = applicationRepository;
    this.configurationRepository = configurationRepository;
  }

  @PostConstruct
  public void populate() {

    if (configurationRepository.existsById(Configuration.DEFAULT_APP)) {
      // Test database already initialized for integration tests
      return;
    }

    GeoService test = new GeoService();
    test.setProtocol("wms");
    test.setUrl("https://snapshot.tailormap.nl/geoserver/wms");
    test.setTitle("Test GeoServer");

    geoServiceRepository.save(test);

    // geoServiceHelper.loadServiceCapabilities(test);

    Application app = new Application();
    app.setName("default");
    app.setTitle("Tailormap demo");
    app.setCrs("EPSG:28992");
    app.setContentRoot(
        new AppContent()
            .addLayersItem(
                new AppLayerRef().serviceId(test.getId()).layerName("postgis:begroeidterreindeel"))
            .addLayersItem(new AppLayerRef().serviceId(test.getId()).layerName("sqlserver:wegdeel"))
            .addLayersItem(new AppLayerRef().serviceId(test.getId()).layerName("BGT")));
    app.setInitialExtent(new Bounds().minx(130011d).miny(458031d).maxx(132703d).maxy(459995d));
    app.setMaxExtent(new Bounds().minx(-285401d).miny(22598d).maxx(595401d).maxy(903401d));
    applicationRepository.save(app);

    app = new Application();
    app.setName("web-mercator");
    app.setCrs("EPSG:3857");
    app.setTitle("Web Mercator");
    applicationRepository.save(app);

    Configuration config = new Configuration();
    config.setKey(Configuration.DEFAULT_APP);
    config.setValue("default");
    configurationRepository.save(config);

    log.info("Test entities created");
  }
}
