/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.configuration.dev;

import static nl.b3p.tailormap.api.persistence.json.GeoServiceProtocol.WMS;
import static nl.b3p.tailormap.api.persistence.json.GeoServiceProtocol.WMTS;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
import nl.b3p.tailormap.api.persistence.json.AppLayerSettings;
import nl.b3p.tailormap.api.persistence.json.AppSettings;
import nl.b3p.tailormap.api.persistence.json.AppTreeLayerNode;
import nl.b3p.tailormap.api.persistence.json.AppTreeLevelNode;
import nl.b3p.tailormap.api.persistence.json.AppTreeNode;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;

/**
 * Populates entities to add services and applications to demo functionality, support development
 * and use in integration tests with a common set of test data. See README.md for usage details.
 */
@org.springframework.context.annotation.Configuration
@ConditionalOnProperty(name = "tailormap-api.database.populate-testdata", havingValue = "true")
public class PopulateTestData {

  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Value("${spatial.dbs.connect:false}")
  private boolean connectToSpatialDbs;

  @Value("${spatial.dbs.localhost:true}")
  private boolean connectToSpatialDbsAtLocalhost;

  @Value("${tailormap-api.database.populate-testdata.admin-hashed-password}")
  private String adminHashedPassword;

  private final UserRepository userRepository;
  private final GroupRepository groupRepository;
  private final CatalogRepository catalogRepository;
  private final GeoServiceRepository geoServiceRepository;
  private final GeoServiceHelper geoServiceHelper;

  private final FeatureSourceRepository featureSourceRepository;
  private final ApplicationRepository applicationRepository;
  private final ConfigurationRepository configurationRepository;

  public PopulateTestData(
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

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void populate() throws Exception {
    InternalAdminAuthentication.setInSecurityContext();
    try {
      // Used in conjunction with tailormap-api.database.clean=true so the database has been cleaned
      // and the latest schema re-created
      createTestUsers();
      createTestConfiguration();
    } finally {
      InternalAdminAuthentication.clearSecurityContextAuthentication();
    }
  }

  public void createTestUsers() throws NoSuchElementException {
    // User with access to any app which requires authentication
    User u = new User().setUsername("user").setPassword("{noop}user").setEmail("user@example.com");
    u.getGroups().add(groupRepository.findById(Group.APP_AUTHENTICATED).orElseThrow());
    userRepository.save(u);

    // Superuser with all access (even admin-users without explicitly having that authority)
    u = new User().setUsername("tm-admin").setPassword(adminHashedPassword);
    u.getGroups().add(groupRepository.findById(Group.ADMIN).orElseThrow());
    userRepository.save(u);
  }

  @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
  public void createTestConfiguration() throws Exception {

    Catalog catalog = catalogRepository.findById(Catalog.MAIN).orElseThrow();
    CatalogNode rootCatalogNode = catalog.getNodes().get(0);
    CatalogNode catalogNode = new CatalogNode().id("test").title("Test services");
    rootCatalogNode.addChildrenItem(catalogNode.getId());
    catalog.getNodes().add(catalogNode);

    Collection<GeoService> services =
        List.of(
            new GeoService()
                .setId("snapshot-geoserver")
                .setProtocol(WMS)
                .setTitle("Test GeoServer")
                .setUrl("https://snapshot.tailormap.nl/geoserver/wms")
                .setPublished(true),
            new GeoService()
                .setId("snapshot-geoserver-proxied")
                .setProtocol(WMS)
                .setTitle("Test GeoServer (proxied)")
                .setUrl("https://snapshot.tailormap.nl/geoserver/wms")
                .setSettings(new GeoServiceSettings().useProxy(true)),
            new GeoService()
                .setId("openbasiskaart")
                .setProtocol(WMTS)
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
            new GeoService()
                .setId("openbasiskaart-proxied")
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
                                    .hiDpiMode(TileLayerHiDpiMode.SUBSTITUTELAYERSHOWNEXTZOOMLEVEL)
                                    .hiDpiSubstituteLayer("osm-hq")))),
            new GeoService()
                .setId("pdok-hwh-luchtfotorgb")
                .setProtocol(WMTS)
                .setTitle("PDOK HWH luchtfoto")
                .setUrl("https://service.pdok.nl/hwh/luchtfotorgb/wmts/v1_0")
                .setPublished(true)
                .setSettings(
                    new GeoServiceSettings()
                        .defaultLayerSettings(
                            new GeoServiceDefaultLayerSettings().hiDpiDisabled(false))),
            new GeoService()
                .setId("at-basemap")
                .setProtocol(WMTS)
                .setTitle("basemap.at")
                .setUrl("https://basemap.at/wmts/1.0.0/WMTSCapabilities.xml")
                .setPublished(true)
                .setSettings(
                    new GeoServiceSettings()
                        .defaultLayerSettings(
                            new GeoServiceDefaultLayerSettings().hiDpiDisabled(true))
                        .layerSettings(
                            Map.of(
                                "geolandbasemap",
                                new GeoServiceLayerSettings()
                                    .hiDpiDisabled(false)
                                    .hiDpiMode(TileLayerHiDpiMode.SUBSTITUTELAYERTILEPIXELRATIOONLY)
                                    .hiDpiSubstituteLayer("bmaphidpi"),
                                "bmaporthofoto30cm",
                                new GeoServiceLayerSettings().hiDpiDisabled(false)))),
            new GeoService()
                .setId("pdok-kadaster-bestuurlijkegebieden")
                .setProtocol(WMS)
                .setUrl(
                    "https://service.pdok.nl/kadaster/bestuurlijkegebieden/wms/v1_0?service=WMS")
                .setSettings(
                    new GeoServiceSettings()
                        .serverType(GeoServiceSettings.ServerTypeEnum.MAPSERVER))
                .setPublished(true)
            // TODO MapServer WMS "https://wms.geonorge.no/skwms1/wms.adm_enheter_historisk"
            );

    for (GeoService geoService : services) {
      geoServiceHelper.loadServiceCapabilities(geoService);

      geoServiceRepository.save(geoService);
      catalogNode.addItemsItem(
          new TailormapObjectRef()
              .kind(TailormapObjectRef.KindEnum.GEO_SERVICE)
              .id(geoService.getId()));
    }

    CatalogNode wfsFeatureSourceCatalogNode =
        new CatalogNode().id("wfs_feature_sources").title("WFS feature sources");
    rootCatalogNode.addChildrenItem(wfsFeatureSourceCatalogNode.getId());
    catalog.getNodes().add(wfsFeatureSourceCatalogNode);

    services.stream()
        .filter(s -> s.getProtocol() == WMS)
        .forEach(
            s -> {
              geoServiceHelper.findAndSaveRelatedWFS(s);
              List<TMFeatureSource> linkedSources =
                  featureSourceRepository.findByLinkedServiceId(s.getId());
              for (TMFeatureSource linkedSource : linkedSources) {
                wfsFeatureSourceCatalogNode.addItemsItem(
                    new TailormapObjectRef()
                        .kind(TailormapObjectRef.KindEnum.FEATURE_SOURCE)
                        .id(linkedSource.getId().toString()));
              }
            });

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
                            .host(connectToSpatialDbsAtLocalhost ? "127.0.0.1" : "postgis")
                            .port(connectToSpatialDbsAtLocalhost ? 54322 : 5432)
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
                            .host(connectToSpatialDbsAtLocalhost ? "127.0.0.1" : "postgis")
                            .port(connectToSpatialDbsAtLocalhost ? 54322 : 5432)
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
                            .host(connectToSpatialDbsAtLocalhost ? "127.0.0.1" : "oracle")
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
                            .host(connectToSpatialDbsAtLocalhost ? "127.0.0.1" : "sqlserver")
                            .database("geodata;encrypt=false")
                            .schema("dbo"))
                    .setAuthentication(
                        new ServiceAuthentication()
                            .method(ServiceAuthentication.MethodEnum.PASSWORD)
                            .username("geodata")
                            .password(geodataPassword)));
    featureSourceRepository.saveAll(featureSources.values());

    CatalogNode featureSourceCatalogNode =
        new CatalogNode().id("feature_sources").title("Test feature sources");
    rootCatalogNode.addChildrenItem(featureSourceCatalogNode.getId());
    catalog.getNodes().add(featureSourceCatalogNode);

    for (TMFeatureSource featureSource : featureSources.values()) {
      featureSourceCatalogNode.addItemsItem(
          new TailormapObjectRef()
              .kind(TailormapObjectRef.KindEnum.FEATURE_SOURCE)
              .id(featureSource.getId().toString()));
    }

    catalogRepository.save(catalog);

    if (connectToSpatialDbs) {
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

      services.stream()
          .filter(s -> s.getId().startsWith("snapshot-geoserver"))
          .forEach(
              s ->
                  s.getSettings()
                      .layerSettings(
                          Map.of(
                              "postgis:begroeidterreindeel",
                                  new GeoServiceLayerSettings()
                                      .featureType(
                                          new FeatureTypeRef()
                                              .featureSourceId(
                                                  featureSources.get("postgis").getId())),
                              "sqlserver:wegdeel",
                                  new GeoServiceLayerSettings()
                                      .featureType(
                                          new FeatureTypeRef()
                                              .featureSourceId(
                                                  featureSources.get("sqlserver").getId())),
                              "oracle:WATERDEEL",
                                  new GeoServiceLayerSettings()
                                      .featureType(
                                          new FeatureTypeRef()
                                              .featureSourceId(
                                                  featureSources.get("oracle").getId())))));
    }

    List<AppTreeNode> baseNodes =
        List.of(
            new AppTreeLevelNode()
                .objectType("AppTreeLevelNode")
                .id("lvl:openbasiskaart")
                .title("Openbasiskaart")
                .addChildrenIdsItem("lyr:openbasiskaart:osm"),
            new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:openbasiskaart:osm")
                .serviceId("openbasiskaart")
                .layerName("osm")
                .visible(true),
            new AppTreeLevelNode()
                .objectType("AppTreeLevelNode")
                .id("lvl:pdok-hwh-luchtfotorgb")
                .title("Luchtfoto")
                .addChildrenIdsItem("lyr:pdok-hwh-luchtfotorgb:Actueel_orthoHR"),
            new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:pdok-hwh-luchtfotorgb:Actueel_orthoHR")
                .serviceId("pdok-hwh-luchtfotorgb")
                .layerName("Actueel_orthoHR")
                .visible(false));

    Application app =
        new Application()
            .setName("default")
            .setTitle("Tailormap demo")
            .setCrs("EPSG:28992")
            .setContentRoot(
                new AppContent()
                    .addBaseLayerNodesItem(
                        new AppTreeLevelNode()
                            .objectType("AppTreeLevelNode")
                            .id("root-base-layers")
                            .root(true)
                            .title("Base layers")
                            .childrenIds(
                                List.of(
                                    "lvl:openbasiskaart",
                                    "lvl:pdok-hwh-luchtfotorgb",
                                    "lvl:openbasiskaart-proxied")))
                    .addBaseLayerNodesItem(
                        new AppTreeLevelNode()
                            .objectType("AppTreeLevelNode")
                            .id("lvl:openbasiskaart-proxied")
                            .title("Openbasiskaart (proxied)")
                            .addChildrenIdsItem("lyr:openbasiskaart-proxied:osm"))
                    .addBaseLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:openbasiskaart-proxied:osm")
                            .serviceId("openbasiskaart-proxied")
                            .layerName("osm")
                            .visible(false))
                    .addLayerNodesItem(
                        new AppTreeLevelNode()
                            .objectType("AppTreeLevelNode")
                            .id("root")
                            .root(true)
                            .title("Layers")
                            .childrenIds(
                                List.of(
                                    "lyr:snapshot-geoserver:postgis:begroeidterreindeel",
                                    "lyr:snapshot-geoserver:sqlserver:wegdeel",
                                    "lyr:snapshot-geoserver:oracle:WATERDEEL",
                                    "lyr:snapshot-geoserver:BGT",
                                    "lvl:proxied")))
                    .addLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:snapshot-geoserver:postgis:begroeidterreindeel")
                            .serviceId("snapshot-geoserver")
                            .layerName("postgis:begroeidterreindeel")
                            .visible(true))
                    .addLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:snapshot-geoserver:sqlserver:wegdeel")
                            .serviceId("snapshot-geoserver")
                            .layerName("sqlserver:wegdeel")
                            .visible(true))
                    .addLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:snapshot-geoserver:oracle:WATERDEEL")
                            .serviceId("snapshot-geoserver")
                            .layerName("oracle:WATERDEEL")
                            .visible(true))
                    .addLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:snapshot-geoserver:BGT")
                            .serviceId("snapshot-geoserver")
                            .layerName("BGT")
                            .visible(false))
                    .addLayerNodesItem(
                        new AppTreeLevelNode()
                            .objectType("AppTreeLevelNode")
                            .id("lvl:proxied")
                            .title("Proxied")
                            .childrenIds(
                                List.of(
                                    "lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel")))
                    .addLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel")
                            .serviceId("snapshot-geoserver-proxied")
                            .layerName("postgis:begroeidterreindeel")
                            .visible(false)))
            .setSettings(
                new AppSettings()
                    .putLayerSettingsItem(
                        "lyr:snapshot-geoserver:oracle:WATERDEEL",
                        new AppLayerSettings().opacity(50).title("Waterdeel andere titel")));
    app.getContentRoot().getBaseLayerNodes().addAll(baseNodes);
    app.setInitialExtent(new Bounds().minx(130011d).miny(458031d).maxx(132703d).maxy(459995d));
    app.setMaxExtent(new Bounds().minx(-285401d).miny(22598d).maxx(595401d).maxy(903401d));
    applicationRepository.save(app);

    app =
        new Application()
            .setName("base")
            .setTitle("Service base app")
            .setCrs("EPSG:28992")
            .setContentRoot(
                new AppContent()
                    .addBaseLayerNodesItem(
                        new AppTreeLevelNode()
                            .objectType("AppTreeLevelNode")
                            .id("root-base-layers")
                            .root(true)
                            .title("Base layers")
                            .childrenIds(
                                List.of("lvl:openbasiskaart", "lvl:pdok-hwh-luchtfotorgb"))));
    app.getContentRoot().getBaseLayerNodes().addAll(baseNodes);
    applicationRepository.save(app);

    app =
        new Application()
            .setName("austria")
            .setCrs("EPSG:3857")
            .setTitle("Austria")
            .setInitialExtent(
                new Bounds().minx(987982d).miny(5799551d).maxx(1963423d).maxy(6320708d))
            .setContentRoot(
                new AppContent()
                    .addBaseLayerNodesItem(
                        new AppTreeLevelNode()
                            .objectType("AppTreeLevelNode")
                            .id("root-base-layers")
                            .root(true)
                            .title("Base layers")
                            .childrenIds(
                                List.of("lvl:basemap", "lvl:orthofoto", "lvl:orthofoto-labels")))
                    .addBaseLayerNodesItem(
                        new AppTreeLevelNode()
                            .objectType("AppTreeLevelNode")
                            .id("lvl:basemap")
                            .title("Basemap")
                            .addChildrenIdsItem("lyr:at-basemap:geolandbasemap"))
                    .addBaseLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:at-basemap:geolandbasemap")
                            .serviceId("at-basemap")
                            .layerName("geolandbasemap")
                            .visible(true))
                    .addBaseLayerNodesItem(
                        new AppTreeLevelNode()
                            .objectType("AppTreeLevelNode")
                            .id("lvl:orthofoto")
                            .title("Orthofoto")
                            .addChildrenIdsItem("lyr:at-basemap:orthofoto"))
                    .addBaseLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:at-basemap:orthofoto")
                            .serviceId("at-basemap")
                            .layerName("bmaporthofoto30cm")
                            .visible(false))
                    .addBaseLayerNodesItem(
                        new AppTreeLevelNode()
                            .objectType("AppTreeLevelNode")
                            .id("lvl:orthofoto-labels")
                            .title("Orthofoto with labels")
                            .childrenIds(
                                List.of(
                                    "lyr:at-basemap:bmapoverlay", "lyr:at-basemap:orthofoto_2")))
                    .addBaseLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:at-basemap:bmapoverlay")
                            .serviceId("at-basemap")
                            .layerName("bmapoverlay")
                            .visible(false))
                    .addBaseLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:at-basemap:orthofoto_2")
                            .serviceId("at-basemap")
                            .layerName("bmaporthofoto30cm")
                            .visible(false)));

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
    config = new Configuration();
    config.setKey(Configuration.DEFAULT_BASE_APP);
    config.setValue("base");
    configurationRepository.save(config);

    logger.info("Test entities created");
  }
}
