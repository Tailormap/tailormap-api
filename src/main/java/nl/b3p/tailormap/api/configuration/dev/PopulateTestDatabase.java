/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.configuration.dev;

import java.util.Map;
import javax.annotation.PostConstruct;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.Catalog;
import nl.b3p.tailormap.api.persistence.Configuration;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.Group;
import nl.b3p.tailormap.api.persistence.User;
import nl.b3p.tailormap.api.persistence.helper.GeoServiceHelper;
import nl.b3p.tailormap.api.persistence.json.AppContent;
import nl.b3p.tailormap.api.persistence.json.AppLayerRef;
import nl.b3p.tailormap.api.persistence.json.BaseLayerInner;
import nl.b3p.tailormap.api.persistence.json.CatalogNode;
import nl.b3p.tailormap.api.persistence.json.GeoServiceDefaultLayerSettings;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayerSettings;
import nl.b3p.tailormap.api.persistence.json.GeoServiceSettings;
import nl.b3p.tailormap.api.persistence.json.TailormapObjectRef;
import nl.b3p.tailormap.api.persistence.json.TileLayerHiDpiMode;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.repository.CatalogRepository;
import nl.b3p.tailormap.api.repository.ConfigurationRepository;
import nl.b3p.tailormap.api.repository.GeoServiceRepository;
import nl.b3p.tailormap.api.repository.UserRepository;
import nl.b3p.tailormap.api.viewer.model.Bounds;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;

@org.springframework.context.annotation.Configuration
@Profile("!test")
// TODO: Only in recreate-db profile
public class PopulateTestDatabase {
  private static final Log log = LogFactory.getLog(PopulateTestDatabase.class);

  private final UserRepository userRepository;
  private final CatalogRepository catalogRepository;
  private final GeoServiceRepository geoServiceRepository;
  private final GeoServiceHelper geoServiceHelper;
  private final ApplicationRepository applicationRepository;
  private final ConfigurationRepository configurationRepository;

  public PopulateTestDatabase(
      UserRepository userRepository,
      CatalogRepository catalogRepository,
      GeoServiceRepository geoServiceRepository,
      GeoServiceHelper geoServiceHelper,
      ApplicationRepository applicationRepository,
      ConfigurationRepository configurationRepository) {
    this.userRepository = userRepository;
    this.catalogRepository = catalogRepository;
    this.geoServiceRepository = geoServiceRepository;
    this.geoServiceHelper = geoServiceHelper;
    this.applicationRepository = applicationRepository;
    this.configurationRepository = configurationRepository;
  }

  @PostConstruct
  @DependsOn("tailormap-database-initialization")
  public void createTestUsers() {
    // User with access to any app which requires authentication
    User u = new User().setUsername("user").setPassword("{noop}user");
    u.getGroups().add(new Group().setName(Group.APP_AUTHENTICATED));
    userRepository.save(u);

    // Only user admin
    u = new User().setUsername("useradmin").setPassword("{noop}useradmin");
    u.getGroups().add(new Group().setName(Group.ADMIN_USERS));
    userRepository.save(u);

    // Superuser with all access (even admin-users without explicitly having that authority)
    u = new User().setUsername("tm-admin").setPassword("{noop}tm-admin");
    u.getGroups().add(new Group().setName(Group.ADMIN));
    userRepository.save(u);
  }

  @PostConstruct
  @DependsOn("tailormap-database-initialization")
  public void populate() throws Exception {

    if (configurationRepository.existsById(Configuration.DEFAULT_APP)) {
      // Test database already initialized for integration tests
      return;
    }

    Catalog catalog = catalogRepository.findById(Catalog.MAIN).get();
    CatalogNode rootCatalogNode = catalog.getNodes().get(0);
    CatalogNode catalogNode = new CatalogNode().id("test").title("Test services");
    rootCatalogNode.addChildrenItem(catalogNode.getId());
    catalog.getNodes().add(catalogNode);

    Map<String, GeoService> services =
        Map.of(
            "geoserver",
            new GeoService()
                .setProtocol("wms")
                .setTitle("Test GeoServer")
                .setUrl("https://snapshot.tailormap.nl/geoserver/wms"),
            "openbasiskaart",
            new GeoService()
                .setProtocol("wmts")
                .setTitle("Openbasiskaart")
                .setUrl("https://www.openbasiskaart.nl/mapcache/wmts")
                .setSettings(
                    new GeoServiceSettings()
                        .layerSettings(
                            Map.of(
                                "osm",
                                new GeoServiceLayerSettings()
                                    .hiDpiMode(TileLayerHiDpiMode.SUBSTITUTELAYERSHOWNEXTZOOMLEVEL)
                                    .hiDpiSubstituteLayer("osm-hq")))),
            "pdok luchtfoto",
            new GeoService()
                .setProtocol("wmts")
                .setTitle("PDOK HWH luchtfoto")
                .setUrl("https://service.pdok.nl/hwh/luchtfotorgb/wmts/v1_0")
                .setSettings(
                    new GeoServiceSettings()
                        .defaultLayerSettings(
                            new GeoServiceDefaultLayerSettings()
                                .hiDpiMode(TileLayerHiDpiMode.SHOWNEXTZOOMLEVEL))),
            "basemap.at",
            new GeoService()
                .setProtocol("wmts")
                .setTitle("basemap.at")
                .setUrl("https://basemap.at/wmts/1.0.0/WMTSCapabilities.xml")
                .setSettings(
                    new GeoServiceSettings()
                        .layerSettings(
                            Map.of(
                                "geolandbasemap",
                                new GeoServiceLayerSettings()
                                    .hiDpiMode(TileLayerHiDpiMode.SUBSTITUTELAYERTILEPIXELRATIOONLY)
                                    .hiDpiSubstituteLayer("bmaphidpi"),
                                "bmaporthofoto30cm",
                                new GeoServiceLayerSettings()
                                    .hiDpiMode(TileLayerHiDpiMode.SHOWNEXTZOOMLEVEL))))
            //        new GeoService()
            //            .setProtocol(WMS)
            //            .setTitle("Norway - Administrative enheter")
            //            .setUrl("https://wms.geonorge.no/skwms1/wms.adm_enheter_historisk")
            //            .setSettings(new
            // GeoServiceSettings().serverType(GeoServiceSettings.ServerTypeEnum.MAPSERVER)),
            );

    for (GeoService geoService : services.values()) {
      geoServiceHelper.loadServiceCapabilities(geoService);
      geoServiceRepository.save(geoService);
      // TODO change GeoService.id to String
      catalogNode.addItemsItem(
          new TailormapObjectRef()
              .kind(TailormapObjectRef.KindEnum.GEO_SERVICE)
              .id(geoService.getId() + ""));
    }
    catalogRepository.save(catalog);

    Long testId = services.get("geoserver").getId();
    Long obkId = services.get("openbasiskaart").getId();
    Long lufoId = services.get("pdok luchtfoto").getId();

    Application app =
        new Application()
            .setName("default")
            .setTitle("Tailormap demo")
            .setCrs("EPSG:28992")
            .setContentRoot(
                new AppContent()
                    .addBaseLayersItem(
                        new BaseLayerInner()
                            .title("Openbasiskaart")
                            .addLayersItem(new AppLayerRef().serviceId(obkId).layerName("osm")))
                    .addBaseLayersItem(
                        new BaseLayerInner()
                            .title("Luchtfoto")
                            .addLayersItem(
                                new AppLayerRef()
                                    .serviceId(lufoId)
                                    .layerName("Actueel_orthoHR")
                                    .visible(false)))
                    .addLayersItem(
                        new AppLayerRef()
                            .serviceId(testId)
                            .layerName("postgis:begroeidterreindeel"))
                    .addLayersItem(
                        new AppLayerRef().serviceId(testId).layerName("sqlserver:wegdeel"))
                    .addLayersItem(new AppLayerRef().serviceId(testId).layerName("BGT")));
    app.setInitialExtent(new Bounds().minx(130011d).miny(458031d).maxx(132703d).maxy(459995d));
    app.setMaxExtent(new Bounds().minx(-285401d).miny(22598d).maxx(595401d).maxy(903401d));
    applicationRepository.save(app);

    Long basemapAtId = services.get("basemap.at").getId();

    app =
        new Application()
            .setName("austria")
            .setCrs("EPSG:3857")
            .setTitle("Austria")
            .setInitialExtent(
                new Bounds().minx(987982d).miny(5799551d).maxx(1963423d).maxy(6320708d))
            .setContentRoot(
                new AppContent()
                    .addBaseLayersItem(
                        new BaseLayerInner()
                            .title("Basemap")
                            .addLayersItem(
                                new AppLayerRef()
                                    .serviceId(basemapAtId)
                                    .layerName("geolandbasemap")))
                    .addBaseLayersItem(
                        new BaseLayerInner()
                            .title("Orthofoto")
                            .addLayersItem(
                                new AppLayerRef()
                                    .serviceId(basemapAtId)
                                    .layerName("bmaporthofoto30cm")
                                    .visible(false)))
                    .addBaseLayersItem(
                        new BaseLayerInner()
                            .title("Orthofoto with labels")
                            .addLayersItem(
                                new AppLayerRef()
                                    .serviceId(basemapAtId)
                                    .layerName("bmapoverlay")
                                    .visible(false))
                            .addLayersItem(
                                new AppLayerRef()
                                    .serviceId(basemapAtId)
                                    .layerName("bmaporthofoto30cm")
                                    .visible(false))));
    applicationRepository.save(app);

    // WMS doesn't work, issue with WMS 1.1.1 vs 1.3.0?
    //    app =
    //        new Application()
    //            .setName("norway")
    //            .setCrs("EPSG:27397")
    //            .setTitle("Norway")
    //            .setContentRoot(
    //                new AppContent()
    //                    .addLayersItem(
    //                        new
    // AppLayerRef().serviceId(5L).layerName("adm_enheter_historisk_WMS")));
    //    applicationRepository.save(app);

    Configuration config = new Configuration();
    config.setKey(Configuration.DEFAULT_APP);
    config.setValue("default");
    configurationRepository.save(config);

    log.info("Test entities created");
  }
}
