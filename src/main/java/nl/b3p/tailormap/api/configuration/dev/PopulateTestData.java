/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.configuration.dev;

import static nl.b3p.tailormap.api.persistence.json.GeoServiceProtocol.WMS;
import static nl.b3p.tailormap.api.persistence.json.GeoServiceProtocol.WMTS;
import static nl.b3p.tailormap.api.persistence.json.GeoServiceProtocol.XYZ;
import static nl.b3p.tailormap.api.security.AuthorizationService.ACCESS_TYPE_READ;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import nl.b3p.tailormap.api.geotools.featuresources.JDBCFeatureSourceHelper;
import nl.b3p.tailormap.api.geotools.featuresources.WFSFeatureSourceHelper;
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
import nl.b3p.tailormap.api.persistence.json.AttributeSettings;
import nl.b3p.tailormap.api.persistence.json.AuthorizationRule;
import nl.b3p.tailormap.api.persistence.json.AuthorizationRuleDecision;
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
import nl.b3p.tailormap.api.viewer.model.Component;
import nl.b3p.tailormap.api.viewer.model.ComponentConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
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

  @Value("${tailormap-api.database.populate-testdata.exit:false}")
  private boolean exit;

  @Value("${MAP5_URL:#{null}}")
  private String map5url;

  private final ApplicationContext appContext;
  private final UserRepository userRepository;
  private final GroupRepository groupRepository;
  private final CatalogRepository catalogRepository;
  private final GeoServiceRepository geoServiceRepository;
  private final GeoServiceHelper geoServiceHelper;

  private final FeatureSourceRepository featureSourceRepository;
  private final ApplicationRepository applicationRepository;
  private final ConfigurationRepository configurationRepository;

  public PopulateTestData(
      ApplicationContext appContext,
      UserRepository userRepository,
      GroupRepository groupRepository,
      CatalogRepository catalogRepository,
      GeoServiceRepository geoServiceRepository,
      GeoServiceHelper geoServiceHelper,
      FeatureSourceRepository featureSourceRepository,
      ApplicationRepository applicationRepository,
      ConfigurationRepository configurationRepository) {
    this.appContext = appContext;
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
      createTestUsersAndGroups();
      createTestConfiguration();
    } finally {
      InternalAdminAuthentication.clearSecurityContextAuthentication();
    }
    if (exit) {
      // Exit after transaction is completed - for 'mvn verify' to populate testdata before
      // integration tests
      new Thread(
              () -> {
                try {
                  Thread.sleep(5000);

                } catch (InterruptedException ignored) {
                  // Ignore
                }
                SpringApplication.exit(appContext, () -> 0);
                System.exit(0);
              })
          .start();
    }
  }

  public void createTestUsersAndGroups() throws NoSuchElementException {
    Group groupFoo = new Group().setName("test-foo").setDescription("Used for integration tests.");
    groupRepository.save(groupFoo);

    Group groupBar = new Group().setName("test-bar").setDescription("Used for integration tests.");
    groupRepository.save(groupBar);

    Group groupBaz = new Group().setName("test-baz").setDescription("Used for integration tests.");
    groupRepository.save(groupBaz);

    // Normal user
    User u = new User().setUsername("user").setPassword("{noop}user").setEmail("user@example.com");
    u.getGroups().addAll(List.of(groupFoo, groupBar, groupBaz));
    userRepository.save(u);

    // Superuser with all access
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

    List<AuthorizationRule> rule =
        List.of(
            new AuthorizationRule()
                .groupName(Group.ANONYMOUS)
                .decisions(Map.of(ACCESS_TYPE_READ, AuthorizationRuleDecision.ALLOW)));

    List<AuthorizationRule> ruleLoggedIn =
        List.of(
            new AuthorizationRule()
                .groupName(Group.AUTHENTICATED)
                .decisions(Map.of(ACCESS_TYPE_READ, AuthorizationRuleDecision.ALLOW)));

    String osmAttribution =
        "&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors";

    Bounds rdTileGridExtent =
        new Bounds().minx(-285401.92).maxx(595401.92).miny(22598.08).maxy(903401.92);

    Collection<GeoService> services =
        List.of(
            new GeoService()
                .setId("osm")
                .setProtocol(XYZ)
                .setTitle("OSM")
                .setUrl("https://tile.openstreetmap.org/{z}/{x}/{y}.png")
                .setAuthorizationRules(rule)
                .setSettings(
                    new GeoServiceSettings()
                        .xyzCrs("EPSG:3857")
                        .layerSettings(
                            Map.of(
                                "xyz",
                                new GeoServiceLayerSettings()
                                    .attribution(osmAttribution)
                                    .maxZoom(19)))),
            // Layer settings configured later, using the same settings for this one and proxied one
            new GeoService()
                .setId("snapshot-geoserver")
                .setProtocol(WMS)
                .setTitle("Test GeoServer")
                .setUrl("https://snapshot.tailormap.nl/geoserver/wms")
                .setAuthorizationRules(rule)
                .setPublished(true),
            new GeoService()
                .setId("filtered-snapshot-geoserver")
                .setProtocol(WMS)
                .setTitle("Test GeoServer (with authorization rules)")
                .setUrl("https://snapshot.tailormap.nl/geoserver/wms")
                .setAuthorizationRules(
                    List.of(
                        new AuthorizationRule()
                            .groupName("test-foo")
                            .decisions(Map.of(ACCESS_TYPE_READ, AuthorizationRuleDecision.ALLOW)),
                        new AuthorizationRule()
                            .groupName("test-baz")
                            .decisions(Map.of(ACCESS_TYPE_READ, AuthorizationRuleDecision.ALLOW))))
                .setSettings(
                    new GeoServiceSettings()
                        .layerSettings(
                            Map.of(
                                "BGT",
                                new GeoServiceLayerSettings()
                                    .addAuthorizationRulesItem(
                                        new AuthorizationRule()
                                            .groupName("test-foo")
                                            .decisions(
                                                Map.of(
                                                    ACCESS_TYPE_READ,
                                                    AuthorizationRuleDecision.DENY)))
                                    .addAuthorizationRulesItem(
                                        new AuthorizationRule()
                                            .groupName("test-baz")
                                            .decisions(
                                                Map.of(
                                                    ACCESS_TYPE_READ,
                                                    AuthorizationRuleDecision.ALLOW))))))
                .setPublished(true),
            new GeoService()
                .setId("snapshot-geoserver-proxied")
                .setProtocol(WMS)
                .setTitle("Test GeoServer (proxied)")
                .setUrl("https://snapshot.tailormap.nl/geoserver/wms")
                .setAuthorizationRules(rule)
                .setSettings(new GeoServiceSettings().useProxy(true)),
            new GeoService()
                .setId("openbasiskaart")
                .setProtocol(WMTS)
                .setTitle("Openbasiskaart")
                .setUrl("https://www.openbasiskaart.nl/mapcache/wmts")
                .setAuthorizationRules(rule)
                .setSettings(
                    new GeoServiceSettings()
                        .defaultLayerSettings(
                            new GeoServiceDefaultLayerSettings().attribution(osmAttribution))
                        .layerSettings(
                            Map.of(
                                "osm",
                                new GeoServiceLayerSettings()
                                    .title("Openbasiskaart")
                                    .hiDpiMode(TileLayerHiDpiMode.SUBSTITUTELAYERSHOWNEXTZOOMLEVEL)
                                    .hiDpiSubstituteLayer("osm-hq")))),
            new GeoService()
                .setId("openbasiskaart-proxied")
                .setProtocol(WMTS)
                .setTitle("Openbasiskaart (proxied)")
                .setUrl("https://www.openbasiskaart.nl/mapcache/wmts")
                .setAuthorizationRules(rule)
                // The service actually doesn't require authentication, but also doesn't mind it
                // Just for testing
                .setAuthentication(
                    new ServiceAuthentication()
                        .method(ServiceAuthentication.MethodEnum.PASSWORD)
                        .username("test")
                        .password("test"))
                .setSettings(
                    new GeoServiceSettings()
                        .useProxy(true)
                        .defaultLayerSettings(
                            new GeoServiceDefaultLayerSettings().attribution(osmAttribution))
                        .layerSettings(
                            Map.of(
                                "osm",
                                new GeoServiceLayerSettings()
                                    .hiDpiMode(TileLayerHiDpiMode.SUBSTITUTELAYERSHOWNEXTZOOMLEVEL)
                                    .hiDpiSubstituteLayer("osm-hq")))),
            new GeoService()
                .setId("openbasiskaart-tms")
                .setProtocol(XYZ)
                .setTitle("Openbasiskaart (TMS)")
                .setUrl("https://openbasiskaart.nl/mapcache/tms/1.0.0/osm@rd/{z}/{x}/{-y}.png")
                .setAuthorizationRules(rule)
                .setSettings(
                    new GeoServiceSettings()
                        .xyzCrs("EPSG:28992")
                        .defaultLayerSettings(
                            new GeoServiceDefaultLayerSettings().attribution(osmAttribution))
                        .layerSettings(
                            Map.of(
                                "xyz",
                                new GeoServiceLayerSettings()
                                    .maxZoom(15)
                                    .tileGridExtent(rdTileGridExtent)
                                    .hiDpiMode(TileLayerHiDpiMode.SUBSTITUTELAYERTILEPIXELRATIOONLY)
                                    .hiDpiSubstituteLayer(
                                        "https://openbasiskaart.nl/mapcache/tms/1.0.0/osm-hq@rd-hq/{z}/{x}/{-y}.png")))),
            new GeoService()
                .setId("pdok-hwh-luchtfotorgb")
                .setProtocol(WMTS)
                .setTitle("PDOK HWH luchtfoto")
                .setUrl("https://service.pdok.nl/hwh/luchtfotorgb/wmts/v1_0")
                .setAuthorizationRules(rule)
                .setPublished(true)
                .setSettings(
                    new GeoServiceSettings()
                        .defaultLayerSettings(
                            new GeoServiceDefaultLayerSettings()
                                .attribution(
                                    "&copy; <a href=\"https://beeldmateriaal.nl/\">Beeldmateriaal.nl</a>")
                                .hiDpiDisabled(false))
                        .putLayerSettingsItem(
                            "Actueel_orthoHR", new GeoServiceLayerSettings().title("Luchtfoto"))),
            new GeoService()
                .setId("b3p-mapproxy-luchtfoto")
                .setProtocol(XYZ)
                .setTitle("Luchtfoto (TMS)")
                .setUrl("https://mapproxy.b3p.nl/tms/1.0.0/luchtfoto/EPSG28992/{z}/{x}/{-y}.jpeg")
                .setAuthorizationRules(rule)
                .setPublished(true)
                .setSettings(
                    new GeoServiceSettings()
                        .xyzCrs("EPSG:28992")
                        .defaultLayerSettings(
                            new GeoServiceDefaultLayerSettings()
                                .attribution(
                                    "&copy; <a href=\"https://beeldmateriaal.nl/\">Beeldmateriaal.nl</a>")
                                .hiDpiDisabled(false))
                        .layerSettings(
                            Map.of(
                                "xyz",
                                new GeoServiceLayerSettings()
                                    .maxZoom(14)
                                    .tileGridExtent(rdTileGridExtent)
                                    .hiDpiMode(TileLayerHiDpiMode.SHOWNEXTZOOMLEVEL)))),
            new GeoService()
                .setId("at-basemap")
                .setProtocol(WMTS)
                .setTitle("basemap.at")
                .setUrl("https://basemap.at/wmts/1.0.0/WMTSCapabilities.xml")
                .setAuthorizationRules(rule)
                .setPublished(true)
                .setSettings(
                    new GeoServiceSettings()
                        .defaultLayerSettings(
                            new GeoServiceDefaultLayerSettings()
                                .attribution(
                                    "&copy; <a href=\"https://basemap.at/\">basemap.at</a>")
                                .hiDpiDisabled(true))
                        .layerSettings(
                            Map.of(
                                "geolandbasemap",
                                new GeoServiceLayerSettings()
                                    .title("Basemap")
                                    .hiDpiDisabled(false)
                                    .hiDpiMode(TileLayerHiDpiMode.SUBSTITUTELAYERTILEPIXELRATIOONLY)
                                    .hiDpiSubstituteLayer("bmaphidpi"),
                                "bmaporthofoto30cm",
                                new GeoServiceLayerSettings()
                                    .title("Orthophoto")
                                    .hiDpiDisabled(false)))),
            new GeoService()
                .setId("pdok-kadaster-bestuurlijkegebieden")
                .setProtocol(WMS)
                .setUrl(
                    "https://service.pdok.nl/kadaster/bestuurlijkegebieden/wms/v1_0?service=WMS")
                .setAuthorizationRules(rule)
                .setSettings(
                    new GeoServiceSettings()
                        .defaultLayerSettings(
                            new GeoServiceDefaultLayerSettings()
                                .description("This layer shows an administrative boundary."))
                        // No attribution required: service is CC0
                        .serverType(GeoServiceSettings.ServerTypeEnum.MAPSERVER)
                        .useProxy(true))
                .setPublished(true)
                .setTitle("PDOK Kadaster bestuurlijke gebieden")
            // TODO MapServer WMS "https://wms.geonorge.no/skwms1/wms.adm_enheter_historisk"
            );

    if (map5url != null) {
      GeoServiceLayerSettings osmAttr = new GeoServiceLayerSettings().attribution(osmAttribution);
      GeoServiceLayerSettings map5Attr =
          new GeoServiceLayerSettings()
              .attribution(
                  "Kaarten: <a href=\"https://map5.nl\">Map5.nl</a>, data: " + osmAttribution);
      services = new ArrayList<>(services);
      services.add(
          new GeoService()
              .setId("map5")
              .setProtocol(WMTS)
              .setTitle("Map5")
              .setUrl(map5url)
              .setAuthorizationRules(rule)
              .setSettings(
                  new GeoServiceSettings()
                      .defaultLayerSettings(
                          new GeoServiceDefaultLayerSettings().hiDpiDisabled(true))
                      .layerSettings(
                          Map.of(
                              "openlufo",
                                  new GeoServiceLayerSettings()
                                      .attribution(
                                          "&copy; <a href=\"https://beeldmateriaal.nl/\">Beeldmateriaal.nl</a>, "
                                              + osmAttribution),
                              "luforoadslabels", osmAttr,
                              "map5topo", map5Attr,
                              "map5topo_gray", map5Attr,
                              "map5topo_simple", map5Attr,
                              "map5topo_simple_gray", map5Attr,
                              "opensimpletopo", osmAttr,
                              "opensimpletopo_gray", osmAttr,
                              "opentopo", osmAttr,
                              "opentopo_gray", osmAttr))));
    }

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
                        .schema("public")
                        .additionalProperties(
                            Map.of("connectionOptions", "?ApplicationName=tailormap-api")))
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
                        .schema("osm")
                        .additionalProperties(
                            Map.of("connectionOptions", "?ApplicationName=tailormap-api")))
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
                        .schema("GEODATA")
                        .additionalProperties(
                            Map.of("connectionOptions", "?oracle.jdbc.J2EE13Compliant=true")))
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
                        .database("geodata")
                        .schema("dbo")
                        .additionalProperties(Map.of("connectionOptions", ";encrypt=false")))
                .setAuthentication(
                    new ServiceAuthentication()
                        .method(ServiceAuthentication.MethodEnum.PASSWORD)
                        .username("geodata")
                        .password(geodataPassword)),
            "pdok-kadaster-bestuurlijkegebieden",
            new TMFeatureSource()
                .setProtocol(TMFeatureSource.Protocol.WFS)
                .setUrl(
                    "https://service.pdok.nl/kadaster/bestuurlijkegebieden/wfs/v1_0?VERSION=2.0.0")
                .setTitle("Bestuurlijke gebieden")
                .setNotes(
                    "Overzicht van de bestuurlijke indeling van Nederland in gemeenten en provincies alsmede de rijksgrens. Gegevens zijn afgeleid uit de Basisregistratie Kadaster (BRK)."));
    featureSourceRepository.saveAll(featureSources.values());

    new WFSFeatureSourceHelper()
        .loadCapabilities(featureSources.get("pdok-kadaster-bestuurlijkegebieden"));
    geoServiceRepository
        .findById("pdok-kadaster-bestuurlijkegebieden")
        .ifPresent(
            geoService -> {
              geoService
                  .getSettings()
                  .layerSettings(
                      Map.of(
                          "Provinciegebied",
                          new GeoServiceLayerSettings()
                              .description(
                                  "The administrative boundary of Dutch Provinces, connected to a WFS.")
                              .featureType(
                                  new FeatureTypeRef()
                                      .featureSourceId(
                                          featureSources
                                              .get("pdok-kadaster-bestuurlijkegebieden")
                                              .getId())
                                      .featureTypeName("bestuurlijkegebieden:Provinciegebied"))
                              .title("Provinciegebied (WFS)")));
              geoServiceRepository.save(geoService);
            });

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
                  if (fs.getProtocol() == TMFeatureSource.Protocol.JDBC) {
                    new JDBCFeatureSourceHelper().loadCapabilities(fs);
                  } else if (fs.getProtocol() == TMFeatureSource.Protocol.WFS) {
                    new WFSFeatureSourceHelper().loadCapabilities(fs);
                  }
                } catch (Exception e) {
                  logger.error(
                      "Error loading capabilities for feature source {}", fs.getTitle(), e);
                }
              });

      services.stream()
          // Set layer settings for both the proxied and non-proxied one, but don't overwrite the
          // authorization rules for the "filtered-snapshot-geoserver" service
          .filter(s -> s.getId().startsWith("snapshot-geoserver"))
          .forEach(
              s ->
                  s.getSettings()
                      .layerSettings(
                          Map.of(
                              "postgis:begroeidterreindeel",
                              new GeoServiceLayerSettings()
                                  .description(
                                      """
                                                  This layer shows data from http://www.postgis.net/

                                                  https://postgis.net/logos/postgis-logo.png""")
                                  .featureType(
                                      new FeatureTypeRef()
                                          .featureSourceId(featureSources.get("postgis").getId())
                                          .featureTypeName("begroeidterreindeel")),
                              "sqlserver:wegdeel",
                              new GeoServiceLayerSettings()
                                  .attribution(
                                      "CC BY 4.0 <a href=\"https://www.nationaalgeoregister.nl/geonetwork/srv/api/records/2cb4769c-b56e-48fa-8685-c48f61b9a319\">BGT/Kadaster</a>")
                                  .description(
                                      """
                                                  This layer shows data from [MS SQL Server](https://learn.microsoft.com/en-us/sql/relational-databases/spatial/spatial-data-sql-server).

                                                  https://social.technet.microsoft.com/wiki/cfs-filesystemfile.ashx/__key/communityserver-components-imagefileviewer/communityserver-wikis-components-files-00-00-00-00-05/1884.SQL_5F00_h_5F00_rgb.png_2D00_550x0.png""")
                                  .featureType(
                                      new FeatureTypeRef()
                                          .featureSourceId(featureSources.get("sqlserver").getId())
                                          .featureTypeName("wegdeel")),
                              "oracle:WATERDEEL",
                              new GeoServiceLayerSettings()
                                  .description("This layer shows data from Oracle Spatial.")
                                  .featureType(
                                      new FeatureTypeRef()
                                          .featureSourceId(featureSources.get("oracle").getId())
                                          .featureTypeName("WATERDEEL")),
                              "postgis:osm_polygon",
                              new GeoServiceLayerSettings()
                                  .description("This layer shows OSM data from postgis.")
                                  .featureType(
                                      new FeatureTypeRef()
                                          .featureSourceId(
                                              featureSources.get("postgis_osm").getId())
                                          .featureTypeName("osm_polygon")))));
    }

    featureSources.get("pdok-kadaster-bestuurlijkegebieden").getFeatureTypes().stream()
        .filter(ft -> ft.getName().equals("bestuurlijkegebieden:Provinciegebied"))
        .findFirst()
        .ifPresent(
            ft -> {
              ft.getSettings().addHideAttributesItem("identificatie");
              ft.getSettings().addHideAttributesItem("ligtInLandCode");
              ft.getSettings().addHideAttributesItem("ligtInLandNaam");
              ft.getSettings().addHideAttributesItem("fuuid");
              ft.getSettings()
                  .putAttributeSettingsItem("naam", new AttributeSettings().title("Naam"));
            });

    featureSources.get("postgis").getFeatureTypes().stream()
        .filter(ft -> ft.getName().equals("begroeidterreindeel"))
        .findFirst()
        .ifPresent(
            ft -> {
              ft.getSettings().addHideAttributesItem("terminationdate");
              ft.getSettings().addHideAttributesItem("geom_kruinlijn");
              ft.getSettings()
                  .putAttributeSettingsItem("gmlid", new AttributeSettings().title("GML ID"));
              ft.getSettings()
                  .putAttributeSettingsItem(
                      "identificatie", new AttributeSettings().title("Identificatie"));
              ft.getSettings()
                  .putAttributeSettingsItem(
                      "tijdstipregistratie", new AttributeSettings().title("Registratie"));
              ft.getSettings()
                  .putAttributeSettingsItem(
                      "eindregistratie", new AttributeSettings().title("Eind registratie"));
              ft.getSettings()
                  .putAttributeSettingsItem("class", new AttributeSettings().title("Klasse"));
              ft.getSettings()
                  .putAttributeSettingsItem(
                      "bronhouder", new AttributeSettings().title("Bronhouder"));
              ft.getSettings()
                  .putAttributeSettingsItem(
                      "inonderzoek", new AttributeSettings().title("In onderzoek"));
              ft.getSettings()
                  .putAttributeSettingsItem(
                      "relatievehoogteligging",
                      new AttributeSettings().title("Relatieve hoogteligging"));
              ft.getSettings()
                  .putAttributeSettingsItem(
                      "bgt_status", new AttributeSettings().title("BGT status"));
              ft.getSettings()
                  .putAttributeSettingsItem(
                      "plus_status", new AttributeSettings().title("Plus-status"));
              ft.getSettings()
                  .putAttributeSettingsItem(
                      "plus_fysiekvoorkomen",
                      new AttributeSettings().title("Plus-fysiek voorkomen"));
              ft.getSettings()
                  .putAttributeSettingsItem(
                      "begroeidterreindeeloptalud", new AttributeSettings().title("Op talud"));
              ft.getSettings().addAttributeOrderItem("identificatie");
              ft.getSettings().addAttributeOrderItem("bronhouder");
              ft.getSettings().addAttributeOrderItem("class");
            });

    List<AppTreeNode> baseNodes =
        List.of(
            new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:openbasiskaart:osm")
                .serviceId("openbasiskaart")
                .layerName("osm")
                .visible(true),
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
            .setAuthorizationRules(rule)
            .setComponents(
                List.of(new Component().type("EDIT").config(new ComponentConfig().enabled(true))))
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
                                    "lyr:openbasiskaart:osm",
                                    "lyr:pdok-hwh-luchtfotorgb:Actueel_orthoHR",
                                    "lyr:openbasiskaart-proxied:osm",
                                    "lyr:openbasiskaart-tms:xyz",
                                    "lyr:b3p-mapproxy-luchtfoto:xyz")))
                    .addBaseLayerNodesItem(
                        // This layer from a secured proxied service should not be proxyable in a
                        // public app, see test_wms_secured_proxy_not_in_public_app() testcase
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:openbasiskaart-proxied:osm")
                            .serviceId("openbasiskaart-proxied")
                            .layerName("osm")
                            .visible(false))
                    .addBaseLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:openbasiskaart-tms:xyz")
                            .serviceId("openbasiskaart-tms")
                            .layerName("xyz")
                            .visible(false))
                    .addBaseLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:b3p-mapproxy-luchtfoto:xyz")
                            .serviceId("b3p-mapproxy-luchtfoto")
                            .layerName("xyz")
                            .visible(false))
                    .addLayerNodesItem(
                        new AppTreeLevelNode()
                            .objectType("AppTreeLevelNode")
                            .id("root")
                            .root(true)
                            .title("Layers")
                            .childrenIds(
                                List.of(
                                    "lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied",
                                    "lyr:pdok-kadaster-bestuurlijkegebieden:Gemeentegebied",
                                    "lyr:snapshot-geoserver:postgis:begroeidterreindeel",
                                    "lyr:snapshot-geoserver:sqlserver:wegdeel",
                                    "lyr:snapshot-geoserver:oracle:WATERDEEL",
                                    "lyr:snapshot-geoserver:BGT",
                                    "lvl:proxied",
                                    "lvl:osm")))
                    .addLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied")
                            .serviceId("pdok-kadaster-bestuurlijkegebieden")
                            .layerName("Provinciegebied")
                            .visible(true))
                    .addLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:pdok-kadaster-bestuurlijkegebieden:Gemeentegebied")
                            .serviceId("pdok-kadaster-bestuurlijkegebieden")
                            .layerName("Gemeentegebied")
                            .visible(true))
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
                            .visible(false))
                    .addLayerNodesItem(
                        new AppTreeLevelNode()
                            .objectType("AppTreeLevelNode")
                            .id("lvl:osm")
                            .title("OSM")
                            .childrenIds(List.of("lyr:snapshot-geoserver:postgis:osm_polygon")))
                    .addLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:snapshot-geoserver:postgis:osm_polygon")
                            .serviceId("snapshot-geoserver")
                            .layerName("postgis:osm_polygon")
                            .visible(false)))
            .setSettings(
                new AppSettings()
                    .putLayerSettingsItem(
                        "lyr:openbasiskaart:osm", new AppLayerSettings().title("Openbasiskaart"))
                    .putLayerSettingsItem(
                        "lyr:pdok-hwh-luchtfotorgb:Actueel_orthoHR",
                        new AppLayerSettings().title("Luchtfoto"))
                    .putLayerSettingsItem(
                        "lyr:openbasiskaart-proxied:osm",
                        new AppLayerSettings().title("Openbasiskaart (proxied)"))
                    .putLayerSettingsItem(
                        "lyr:snapshot-geoserver:oracle:WATERDEEL",
                        new AppLayerSettings()
                            .opacity(50)
                            .title("Waterdeel overridden title")
                            .editable(true)
                            .description(
                                "This is the layer description from the app layer setting.")
                            .attribution(
                                "CC BY 4.0 <a href=\"https://www.nationaalgeoregister.nl/geonetwork/srv/api/records/2cb4769c-b56e-48fa-8685-c48f61b9a319\">BGT/Kadaster</a>"))
                    .putLayerSettingsItem(
                        "lyr:snapshot-geoserver:postgis:osm_polygon",
                        new AppLayerSettings()
                            .description("OpenStreetMap polygon data in EPSG:3857")
                            .opacity(60)
                            .editable(true)
                            .title("OSM Polygon (EPSG:3857)")
                            .attribution(
                                "© <a href=\"https://www.openstreetmap.org/copyright/\">OpenStreetMap</a> contributors"))
                    .putLayerSettingsItem(
                        "lyr:snapshot-geoserver:postgis:begroeidterreindeel",
                        new AppLayerSettings()
                            .editable(true)
                            .addHideAttributesItem("begroeidterreindeeloptalud")
                            .addReadOnlyAttributesItem("eindregistratie"))
                    .putLayerSettingsItem(
                        "lyr:snapshot-geoserver:sqlserver:wegdeel",
                        new AppLayerSettings().editable(true))
                    .putLayerSettingsItem(
                        "lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel",
                        new AppLayerSettings().editable(false)));

    app.getContentRoot().getBaseLayerNodes().addAll(baseNodes);
    app.setInitialExtent(new Bounds().minx(130011d).miny(458031d).maxx(132703d).maxy(459995d));
    app.setMaxExtent(new Bounds().minx(-285401d).miny(22598d).maxx(595401d).maxy(903401d));

    if (map5url != null) {
      AppTreeLevelNode root = (AppTreeLevelNode) app.getContentRoot().getBaseLayerNodes().get(0);
      List<String> childrenIds = new ArrayList<>(root.getChildrenIds());
      childrenIds.add("lyr:map5:map5topo_simple");
      childrenIds.add("lvl:luchtfoto-labels");
      root.setChildrenIds(childrenIds);
      app.getSettings()
          .putLayerSettingsItem("lyr:map5:map5topo_simple", new AppLayerSettings().title("Map5"));
      app.getContentRoot()
          .addBaseLayerNodesItem(
              new AppTreeLayerNode()
                  .objectType("AppTreeLayerNode")
                  .id("lyr:map5:map5topo_simple")
                  .serviceId("map5")
                  .layerName("map5topo_simple")
                  .visible(false))
          .addBaseLayerNodesItem(
              new AppTreeLevelNode()
                  .objectType("AppTreeLevelNode")
                  .id("lvl:luchtfoto-labels")
                  .title("Luchtfoto met labels")
                  .addChildrenIdsItem("lyr:map5:luforoadslabels")
                  .addChildrenIdsItem("lyr:pdok-hwh-luchtfotorgb:Actueel_orthoHR2"))
          .addBaseLayerNodesItem(
              new AppTreeLayerNode()
                  .objectType("AppTreeLayerNode")
                  .id("lyr:map5:luforoadslabels")
                  .serviceId("map5")
                  .layerName("luforoadslabels")
                  .visible(false))
          .addBaseLayerNodesItem(
              new AppTreeLayerNode()
                  .objectType("AppTreeLayerNode")
                  .id("lyr:pdok-hwh-luchtfotorgb:Actueel_orthoHR2")
                  .serviceId("pdok-hwh-luchtfotorgb")
                  .layerName("Actueel_orthoHR")
                  .visible(false));
    }

    applicationRepository.save(app);

    app =
        new Application()
            .setName("base")
            .setTitle("Service base app")
            .setCrs("EPSG:28992")
            .setAuthorizationRules(rule)
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
                                    "lyr:openbasiskaart:osm",
                                    "lyr:pdok-hwh-luchtfotorgb:Actueel_orthoHR"))));
    app.getContentRoot().getBaseLayerNodes().addAll(baseNodes);
    applicationRepository.save(app);

    app =
        new Application()
            .setName("secured")
            .setTitle("secured app")
            .setCrs("EPSG:28992")
            .setAuthorizationRules(ruleLoggedIn)
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
                                    "lyr:openbasiskaart:osm",
                                    "lyr:pdok-hwh-luchtfotorgb:Actueel_orthoHR",
                                    "lyr:openbasiskaart-proxied:osm")))
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
                                    "lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied",
                                    "lyr:pdok-kadaster-bestuurlijkegebieden:Gemeentegebied",
                                    "lvl:proxied")))
                    .addLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:pdok-kadaster-bestuurlijkegebieden:Gemeentegebied")
                            .serviceId("pdok-kadaster-bestuurlijkegebieden")
                            .layerName("Gemeentegebied")
                            .visible(true))
                    .addLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied")
                            .serviceId("pdok-kadaster-bestuurlijkegebieden")
                            .layerName("Provinciegebied")
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
                        "lyr:openbasiskaart-proxied:osm",
                        new AppLayerSettings().title("Openbasiskaart (proxied)")));

    app.getContentRoot().getBaseLayerNodes().addAll(baseNodes);
    applicationRepository.save(app);

    app =
        new Application()
            .setName("secured-auth")
            .setTitle("secured (with authorizations)")
            .setCrs("EPSG:28992")
            .setAuthorizationRules(
                List.of(
                    new AuthorizationRule()
                        .groupName("test-foo")
                        .decisions(Map.of(ACCESS_TYPE_READ, AuthorizationRuleDecision.ALLOW)),
                    new AuthorizationRule()
                        .groupName("test-bar")
                        .decisions(Map.of(ACCESS_TYPE_READ, AuthorizationRuleDecision.ALLOW))))
            .setContentRoot(
                new AppContent()
                    .addLayerNodesItem(
                        new AppTreeLevelNode()
                            .objectType("AppTreeLevelNode")
                            .id("root")
                            .root(true)
                            .title("Layers")
                            .childrenIds(List.of("lyr:needs-auth", "lyr:public")))
                    .addLayerNodesItem(
                        new AppTreeLevelNode()
                            .objectType("AppTreeLevelNode")
                            .id("lvl:public")
                            .title("Public")
                            .childrenIds(List.of("lyr:snapshot-geoserver:BGT")))
                    .addLayerNodesItem(
                        new AppTreeLevelNode()
                            .objectType("AppTreeLevelNode")
                            .id("lvl:needs-auth")
                            .title("Needs auth")
                            .childrenIds(
                                List.of(
                                    "lyr:filtered-snapshot-geoserver:BGT",
                                    "lyr:filtered-snapshot-geoserver:postgis:begroeidterreindeel")))
                    .addLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:filtered-snapshot-geoserver:BGT")
                            .serviceId("filtered-snapshot-geoserver")
                            .layerName("BGT")
                            .visible(true))
                    .addLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:filtered-snapshot-geoserver:postgis:begroeidterreindeel")
                            .serviceId("filtered-snapshot-geoserver")
                            .layerName("postgis:begroeidterreindeel")
                            .visible(true))
                    .addLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:snapshot-geoserver:BGT")
                            .serviceId("snapshot-geoserver")
                            .layerName("BGT")
                            .visible(true)));

    applicationRepository.save(app);

    app =
        new Application()
            .setName("austria")
            .setCrs("EPSG:3857")
            .setAuthorizationRules(rule)
            .setTitle("Austria")
            .setInitialExtent(
                new Bounds().minx(987982d).miny(5799551d).maxx(1963423d).maxy(6320708d))
            .setMaxExtent(new Bounds().minx(206516d).miny(5095461d).maxx(3146930d).maxy(7096232d))
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
                                    "lyr:at-basemap:geolandbasemap",
                                    "lyr:at-basemap:orthofoto",
                                    "lvl:orthofoto-labels",
                                    "lyr:osm:xyz")))
                    .addBaseLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:at-basemap:geolandbasemap")
                            .serviceId("at-basemap")
                            .layerName("geolandbasemap")
                            .visible(true))
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
                            .title("Orthophoto with labels")
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
                            .visible(false))
                    .addBaseLayerNodesItem(
                        new AppTreeLayerNode()
                            .objectType("AppTreeLayerNode")
                            .id("lyr:osm:xyz")
                            .serviceId("osm")
                            .layerName("xyz")
                            .visible(false)));

    applicationRepository.save(app);

    Configuration config = new Configuration();
    config.setKey(Configuration.DEFAULT_APP);
    config.setValue("default");
    configurationRepository.save(config);
    config = new Configuration();
    config.setKey(Configuration.DEFAULT_BASE_APP);
    config.setValue("base");
    configurationRepository.save(config);

    config = new Configuration();
    config.setKey("test");
    config.setAvailableForViewer(true);
    config.setValue("test value");
    config.setJsonValue(
        new ObjectMapper().readTree("{ \"someProperty\": 1, \"nestedObject\": { \"num\": 42 } }"));
    configurationRepository.save(config);

    logger.info("Test entities created");
  }
}
