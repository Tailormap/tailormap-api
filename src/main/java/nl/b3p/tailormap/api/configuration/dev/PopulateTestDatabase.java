/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.configuration.dev;

import static nl.b3p.tailormap.api.persistence.json.GeoServiceProtocol.WMS;
import static nl.b3p.tailormap.api.persistence.json.GeoServiceProtocol.WMTS;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import nl.b3p.tailormap.api.geotools.featuresources.JDBCFeatureSourceHelper;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.Catalog;
import nl.b3p.tailormap.api.persistence.Configuration;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.Group;
import nl.b3p.tailormap.api.persistence.TMFeatureSource;
import nl.b3p.tailormap.api.persistence.User;
import nl.b3p.tailormap.api.persistence.helper.GeoServiceHelper;
import nl.b3p.tailormap.api.persistence.json.AppContent;
import nl.b3p.tailormap.api.persistence.json.AppLayerRef;
import nl.b3p.tailormap.api.persistence.json.BaseLayerInner;
import nl.b3p.tailormap.api.persistence.json.Bounds;
import nl.b3p.tailormap.api.persistence.json.CatalogNode;
import nl.b3p.tailormap.api.persistence.json.FeatureTypeRef;
import nl.b3p.tailormap.api.persistence.json.GeoServiceDefaultLayerSettings;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayerSettings;
import nl.b3p.tailormap.api.persistence.json.GeoServiceSettings;
import nl.b3p.tailormap.api.persistence.json.JDBCConnectionProperties;
import nl.b3p.tailormap.api.persistence.json.ServiceAuthentication;
import nl.b3p.tailormap.api.persistence.json.TailormapObjectRef;
import nl.b3p.tailormap.api.persistence.json.TileLayerHiDpiMode;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.repository.CatalogRepository;
import nl.b3p.tailormap.api.repository.ConfigurationRepository;
import nl.b3p.tailormap.api.repository.FeatureSourceRepository;
import nl.b3p.tailormap.api.repository.GeoServiceRepository;
import nl.b3p.tailormap.api.repository.GroupRepository;
import nl.b3p.tailormap.api.repository.UserRepository;
import nl.b3p.tailormap.api.security.InternalAdminAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.context.annotation.Configuration
@Profile("!test")
// TODO: Only in recreate-db profile
public class PopulateTestDatabase implements EnvironmentAware {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final UserRepository userRepository;
  private final GroupRepository groupRepository;
  private final CatalogRepository catalogRepository;
  private final GeoServiceRepository geoServiceRepository;
  private final GeoServiceHelper geoServiceHelper;

  private final FeatureSourceRepository featureSourceRepository;
  private final ApplicationRepository applicationRepository;
  private final ConfigurationRepository configurationRepository;

  private boolean spatialDbsLocalhost = true;

  private boolean spatialDbsConnect = false;

  public PopulateTestDatabase(
      UserRepository userRepository,
      GroupRepository groupRepository,
      CatalogRepository catalogRepository,
      GeoServiceRepository geoServiceRepository,
      GeoServiceHelper geoServiceHelper,
      FeatureSourceRepository featureSourceRepository,
      ApplicationRepository applicationRepository,
      ConfigurationRepository configurationRepository) {
    this.userRepository = userRepository;
    this.groupRepository = groupRepository;
    this.catalogRepository = catalogRepository;
    this.geoServiceRepository = geoServiceRepository;
    this.geoServiceHelper = geoServiceHelper;
    this.featureSourceRepository = featureSourceRepository;
    this.applicationRepository = applicationRepository;
    this.configurationRepository = configurationRepository;
  }

  @Override
  public void setEnvironment(Environment environment) {
    spatialDbsLocalhost = !"false".equals(environment.getProperty("SPATIAL_DBS_LOCALHOST"));
    spatialDbsConnect = "true".equals(environment.getProperty("SPATIAL_DBS_CONNECT"));
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  @DependsOn("tailormap-database-initialization")
  public void populate() throws Exception {
    InternalAdminAuthentication.setInSecurityContext();
    try {
      if (configurationRepository.existsById(Configuration.DEFAULT_APP)) {
        // Test database already initialized for integration tests
        return;
      }

      createTestUsers();
      createTestConfiguration();
    } finally {
      InternalAdminAuthentication.clearSecurityContextAuthentication();
    }
  }

  public void createTestUsers() {
    // User with access to any app which requires authentication
    User u = new User().setUsername("user").setPassword("{noop}user");
    u.getGroups().add(groupRepository.findById(Group.APP_AUTHENTICATED).get());
    userRepository.save(u);

    // Only user admin
    u = new User().setUsername("useradmin").setPassword("{noop}useradmin");
    u.getGroups().add(groupRepository.findById(Group.ADMIN_USERS).get());
    userRepository.save(u);

    // Superuser with all access (even admin-users without explicitly having that authority)
    u = new User().setUsername("tm-admin").setPassword("{noop}tm-admin");
    u.getGroups().add(groupRepository.findById(Group.ADMIN).get());
    userRepository.save(u);
  }

  @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
  public void createTestConfiguration() throws Exception {

    Catalog catalog = catalogRepository.findById(Catalog.MAIN).get();
    CatalogNode rootCatalogNode = catalog.getNodes().get(0);
    CatalogNode catalogNode = new CatalogNode().id("test").title("Test services");
    rootCatalogNode.addChildrenItem(catalogNode.getId());
    catalog.getNodes().add(catalogNode);

    SortedMap<String, GeoService> services =
        new TreeMap<>(
            Map.of(
                "0-geoserver",
                new GeoService()
                    .setProtocol(WMS)
                    .setTitle("Test GeoServer")
                    .setUrl("https://snapshot.tailormap.nl/geoserver/wms"),
                "0p-geoserver",
                new GeoService()
                    .setProtocol(WMS)
                    .setTitle("Test GeoServer (proxied)")
                    .setUrl("https://snapshot.tailormap.nl/geoserver/wms")
                    .setSettings(new GeoServiceSettings().useProxy(true)),
                "1-openbasiskaart",
                new GeoService()
                    .setProtocol(WMTS)
                    .setTitle("Openbasiskaart")
                    .setUrl("https://www.openbasiskaart.nl/mapcache/wmts")
                    .setSettings(
                        new GeoServiceSettings()
                            .layerSettings(
                                Map.of(
                                    "osm",
                                    new GeoServiceLayerSettings()
                                        .hiDpiMode(
                                            TileLayerHiDpiMode.SUBSTITUTELAYERSHOWNEXTZOOMLEVEL)
                                        .hiDpiSubstituteLayer("osm-hq")))),
                "1p-openbasiskaart",
                new GeoService()
                    .setProtocol(WMTS)
                    .setTitle("Openbasiskaart (proxied)")
                    .setUrl("https://www.openbasiskaart.nl/mapcache/wmts")
                    .setSettings(
                        new GeoServiceSettings()
                            .useProxy(true)
                            .layerSettings(
                                Map.of(
                                    "osm",
                                    new GeoServiceLayerSettings()
                                        .hiDpiMode(
                                            TileLayerHiDpiMode.SUBSTITUTELAYERSHOWNEXTZOOMLEVEL)
                                        .hiDpiSubstituteLayer("osm-hq")))),
                "2-pdok luchtfoto",
                new GeoService()
                    .setProtocol(WMTS)
                    .setTitle("PDOK HWH luchtfoto")
                    .setUrl("https://service.pdok.nl/hwh/luchtfotorgb/wmts/v1_0")
                    .setSettings(
                        new GeoServiceSettings()
                            .defaultLayerSettings(
                                new GeoServiceDefaultLayerSettings()
                                    .hiDpiMode(TileLayerHiDpiMode.SHOWNEXTZOOMLEVEL))),
                "3-basemap.at",
                new GeoService()
                    .setProtocol(WMTS)
                    .setTitle("basemap.at")
                    .setUrl("https://basemap.at/wmts/1.0.0/WMTSCapabilities.xml")
                    .setSettings(
                        new GeoServiceSettings()
                            .layerSettings(
                                Map.of(
                                    "geolandbasemap",
                                    new GeoServiceLayerSettings()
                                        .hiDpiMode(
                                            TileLayerHiDpiMode.SUBSTITUTELAYERTILEPIXELRATIOONLY)
                                        .hiDpiSubstituteLayer("bmaphidpi"),
                                    "bmaporthofoto30cm",
                                    new GeoServiceLayerSettings()
                                        .hiDpiMode(TileLayerHiDpiMode.SHOWNEXTZOOMLEVEL)))),
                "4-bestuurlijke-gebieden",
                new GeoService()
                    .setProtocol(WMS)
                    .setUrl(
                        "https://service.pdok.nl/kadaster/bestuurlijkegebieden/wms/v1_0?service=WMS")
                //        new GeoService()
                //            .setProtocol(WMS)
                //            .setTitle("Norway - Administrative enheter")
                //            .setUrl("https://wms.geonorge.no/skwms1/wms.adm_enheter_historisk")
                //            .setSettings(new
                // GeoServiceSettings().serverType(GeoServiceSettings.ServerTypeEnum.MAPSERVER)),
                ));

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

    services.values().stream()
        .filter(s -> s.getProtocol() == WMS)
        .forEach(geoServiceHelper::findAndSaveRelatedWFS);

    String geodataPassword = "980f1c8A-25933b2";

    Map<String, TMFeatureSource> featureSources =
        Map.of(
            "postgis",
                new TMFeatureSource()
                    .setProtocol(TMFeatureSource.Protocol.JDBC)
                    .setTitle("PostGIS")
                    .setJdbcConnection(
                        new JDBCConnectionProperties()
                            .dbtype(JDBCConnectionProperties.DbtypeEnum.POSTGIS)
                            .host(spatialDbsLocalhost ? "127.0.0.1" : "postgis")
                            .port(spatialDbsLocalhost ? 54322 : 5432)
                            .database("geodata")
                            .schema("public"))
                    .setAuthentication(
                        new ServiceAuthentication()
                            .method(ServiceAuthentication.MethodEnum.PASSWORD)
                            .username("geodata")
                            .password(geodataPassword)),
            "postgis_osm",
                new TMFeatureSource()
                    .setProtocol(TMFeatureSource.Protocol.JDBC)
                    .setTitle("PostGIS OSM")
                    .setJdbcConnection(
                        new JDBCConnectionProperties()
                            .dbtype(JDBCConnectionProperties.DbtypeEnum.POSTGIS)
                            .host(spatialDbsLocalhost ? "127.0.0.1" : "postgis")
                            .port(spatialDbsLocalhost ? 54322 : 5432)
                            .database("geodata")
                            .schema("osm"))
                    .setAuthentication(
                        new ServiceAuthentication()
                            .method(ServiceAuthentication.MethodEnum.PASSWORD)
                            .username("geodata")
                            .password(geodataPassword)),
            "oracle",
                new TMFeatureSource()
                    .setProtocol(TMFeatureSource.Protocol.JDBC)
                    .setTitle("Oracle")
                    .setJdbcConnection(
                        new JDBCConnectionProperties()
                            .dbtype(JDBCConnectionProperties.DbtypeEnum.ORACLE)
                            .host(spatialDbsLocalhost ? "127.0.0.1" : "oracle")
                            .database("/XEPDB1")
                            .schema("GEODATA"))
                    .setAuthentication(
                        new ServiceAuthentication()
                            .method(ServiceAuthentication.MethodEnum.PASSWORD)
                            .username("geodata")
                            .password(geodataPassword)),
            "sqlserver",
                new TMFeatureSource()
                    .setProtocol(TMFeatureSource.Protocol.JDBC)
                    .setTitle("MS SQL Server")
                    .setJdbcConnection(
                        new JDBCConnectionProperties()
                            .dbtype(JDBCConnectionProperties.DbtypeEnum.SQLSERVER)
                            .host(spatialDbsLocalhost ? "127.0.0.1" : "sqlserver")
                            .database("geodata;encrypt=false")
                            .schema("dbo"))
                    .setAuthentication(
                        new ServiceAuthentication()
                            .method(ServiceAuthentication.MethodEnum.PASSWORD)
                            .username("geodata")
                            .password(geodataPassword)));
    featureSourceRepository.saveAll(featureSources.values());

    if (spatialDbsConnect) {
      featureSources
          .values()
          .forEach(
              fs -> {
                try {
                  new JDBCFeatureSourceHelper().loadCapabilities(fs);
                } catch (Exception e) {
                  logger.error(
                      "Error loading capabilities for feature source {}", fs.getTitle(), e);
                }
              });

      services
          .get("0-geoserver")
          .getSettings()
          .layerSettings(
              Map.of(
                  "postgis:begroeidterreindeel",
                      new GeoServiceLayerSettings()
                          .featureType(
                              new FeatureTypeRef()
                                  .featureSourceId(featureSources.get("postgis").getId())),
                  "sqlserver:wegdeel",
                      new GeoServiceLayerSettings()
                          .featureType(
                              new FeatureTypeRef()
                                  .featureSourceId(featureSources.get("sqlserver").getId())),
                  "oracle:WATERDEEL",
                      new GeoServiceLayerSettings()
                          .featureType(
                              new FeatureTypeRef()
                                  .featureSourceId(featureSources.get("oracle").getId()))));
    }

    Long testId = services.get("0-geoserver").getId();
    Long testpId = services.get("0p-geoserver").getId();
    Long obkId = services.get("1-openbasiskaart").getId();
    Long obkpId = services.get("1p-openbasiskaart").getId();
    Long lufoId = services.get("2-pdok luchtfoto").getId();

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
                    .addBaseLayersItem(
                        new BaseLayerInner()
                            .title("Openbasiskaart (proxied)")
                            .addLayersItem(
                                new AppLayerRef()
                                    .serviceId(obkpId)
                                    .layerName("osm")
                                    .visible(false)))
                    .addLayersItem(
                        new AppLayerRef()
                            .serviceId(testId)
                            .layerName("postgis:begroeidterreindeel"))
                    .addLayersItem(
                        new AppLayerRef()
                            .serviceId(testpId)
                            .title("begroeidterreindeel (proxied)")
                            .layerName("postgis:begroeidterreindeel")
                            .visible(false))
                    .addLayersItem(
                        new AppLayerRef().serviceId(testId).layerName("sqlserver:wegdeel"))
                    .addLayersItem(
                        new AppLayerRef().serviceId(testId).layerName("oracle:WATERDEEL"))
                    .addLayersItem(
                        new AppLayerRef().serviceId(testId).layerName("BGT").visible(false)));
    app.setInitialExtent(new Bounds().minx(130011d).miny(458031d).maxx(132703d).maxy(459995d));
    app.setMaxExtent(new Bounds().minx(-285401d).miny(22598d).maxx(595401d).maxy(903401d));
    applicationRepository.save(app);

    Long basemapAtId = services.get("3-basemap.at").getId();

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

    logger.info("Test entities created");
  }
}
