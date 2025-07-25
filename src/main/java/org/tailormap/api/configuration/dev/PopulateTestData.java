/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.configuration.dev;

import static org.tailormap.api.persistence.Configuration.HOME_PAGE;
import static org.tailormap.api.persistence.Configuration.PORTAL_MENU;
import static org.tailormap.api.persistence.json.GeoServiceProtocol.QUANTIZEDMESH;
import static org.tailormap.api.persistence.json.GeoServiceProtocol.TILES3D;
import static org.tailormap.api.persistence.json.GeoServiceProtocol.WMS;
import static org.tailormap.api.persistence.json.GeoServiceProtocol.WMTS;
import static org.tailormap.api.persistence.json.GeoServiceProtocol.XYZ;
import static org.tailormap.api.persistence.json.HiddenLayerFunctionalityEnum.ATTRIBUTE_LIST;
import static org.tailormap.api.persistence.json.HiddenLayerFunctionalityEnum.EXPORT;
import static org.tailormap.api.persistence.json.HiddenLayerFunctionalityEnum.FEATURE_INFO;
import static org.tailormap.api.security.AuthorisationService.ACCESS_TYPE_VIEW;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.apache.solr.client.solrj.SolrServerException;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.PropertyPlaceholderHelper;
import org.tailormap.api.admin.model.TaskSchedule;
import org.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import org.tailormap.api.geotools.featuresources.JDBCFeatureSourceHelper;
import org.tailormap.api.geotools.featuresources.WFSFeatureSourceHelper;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.Catalog;
import org.tailormap.api.persistence.Configuration;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.persistence.Page;
import org.tailormap.api.persistence.SearchIndex;
import org.tailormap.api.persistence.TMFeatureSource;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.Upload;
import org.tailormap.api.persistence.User;
import org.tailormap.api.persistence.helper.GeoServiceHelper;
import org.tailormap.api.persistence.json.AppContent;
import org.tailormap.api.persistence.json.AppLayerSettings;
import org.tailormap.api.persistence.json.AppSettings;
import org.tailormap.api.persistence.json.AppTreeLayerNode;
import org.tailormap.api.persistence.json.AppTreeLevelNode;
import org.tailormap.api.persistence.json.AppTreeNode;
import org.tailormap.api.persistence.json.AppUiSettings;
import org.tailormap.api.persistence.json.AttributeSettings;
import org.tailormap.api.persistence.json.AttributeValueSettings;
import org.tailormap.api.persistence.json.AuthorizationRule;
import org.tailormap.api.persistence.json.AuthorizationRuleDecision;
import org.tailormap.api.persistence.json.Bounds;
import org.tailormap.api.persistence.json.CatalogNode;
import org.tailormap.api.persistence.json.FeatureTypeRef;
import org.tailormap.api.persistence.json.FeatureTypeTemplate;
import org.tailormap.api.persistence.json.Filter;
import org.tailormap.api.persistence.json.FilterEditConfiguration;
import org.tailormap.api.persistence.json.FilterGroup;
import org.tailormap.api.persistence.json.GeoServiceDefaultLayerSettings;
import org.tailormap.api.persistence.json.GeoServiceLayerSettings;
import org.tailormap.api.persistence.json.GeoServiceSettings;
import org.tailormap.api.persistence.json.JDBCConnectionProperties;
import org.tailormap.api.persistence.json.MenuItem;
import org.tailormap.api.persistence.json.PageTile;
import org.tailormap.api.persistence.json.ServiceAuthentication;
import org.tailormap.api.persistence.json.TailormapObjectRef;
import org.tailormap.api.persistence.json.TileLayerHiDpiMode;
import org.tailormap.api.repository.ApplicationRepository;
import org.tailormap.api.repository.CatalogRepository;
import org.tailormap.api.repository.ConfigurationRepository;
import org.tailormap.api.repository.FeatureSourceRepository;
import org.tailormap.api.repository.GeoServiceRepository;
import org.tailormap.api.repository.GroupRepository;
import org.tailormap.api.repository.PageRepository;
import org.tailormap.api.repository.SearchIndexRepository;
import org.tailormap.api.repository.UploadRepository;
import org.tailormap.api.repository.UserRepository;
import org.tailormap.api.scheduling.FailingPocTask;
import org.tailormap.api.scheduling.IndexTask;
import org.tailormap.api.scheduling.InterruptablePocTask;
import org.tailormap.api.scheduling.PocTask;
import org.tailormap.api.scheduling.TMJobDataMap;
import org.tailormap.api.scheduling.Task;
import org.tailormap.api.scheduling.TaskManagerService;
import org.tailormap.api.scheduling.TaskType;
import org.tailormap.api.security.InternalAdminAuthentication;
import org.tailormap.api.solr.SolrHelper;
import org.tailormap.api.solr.SolrService;
import org.tailormap.api.viewer.model.AppStyling;
import org.tailormap.api.viewer.model.Component;
import org.tailormap.api.viewer.model.ComponentConfig;

/**
 * Populates entities to add services and applications to demo functionality, support development and use in integration
 * tests with a common set of test data. See README.md for usage details.
 */
@org.springframework.context.annotation.Configuration
@ConditionalOnProperty(name = "tailormap-api.database.populate-testdata", havingValue = "true")
public class PopulateTestData {

  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final ApplicationContext appContext;
  private final UserRepository userRepository;
  private final GroupRepository groupRepository;
  private final CatalogRepository catalogRepository;
  private final GeoServiceRepository geoServiceRepository;
  private final GeoServiceHelper geoServiceHelper;
  private final SolrService solrService;
  private final TaskManagerService taskManagerService;
  private final FeatureSourceRepository featureSourceRepository;
  private final ApplicationRepository applicationRepository;
  private final ConfigurationRepository configurationRepository;
  private final SearchIndexRepository searchIndexRepository;
  private final FeatureSourceFactoryHelper featureSourceFactoryHelper;
  private final UploadRepository uploadRepository;
  private final PageRepository pageRepository;
  private final JdbcClient jdbcClient;

  @Value("${spatial.dbs.connect:false}")
  private boolean connectToSpatialDbs;

  @Value("#{'${tailormap-api.database.populate-testdata.categories}'.split(',')}")
  private Set<String> categories;

  @Value("${spatial.dbs.localhost:true}")
  private boolean connectToSpatialDbsAtLocalhost;

  @Value("${tailormap-api.database.populate-testdata.admin-hashed-password}")
  private String adminHashedPassword;

  @Value("${tailormap-api.database.populate-testdata.exit:false}")
  private boolean exit;

  @Value("${MAP5_URL:#{null}}")
  private String map5url;

  private final String tiles3dProxyUsername = "b3p";

  // Password is for proxying a public service for testing purposes, not really secret
  private final String tiles3dProxyPassword = "cOxPlJFWmtndFk0eVg5VdL";

  @Value("${tailormap-api.solr-batch-size:1000}")
  private int solrBatchSize;

  @Value("${tailormap-api.solr-geometry-validation-rule:repairBuffer0}")
  private String solrGeometryValidationRule;

  public PopulateTestData(
      ApplicationContext appContext,
      UserRepository userRepository,
      GroupRepository groupRepository,
      CatalogRepository catalogRepository,
      GeoServiceRepository geoServiceRepository,
      GeoServiceHelper geoServiceHelper,
      SolrService solrService,
      TaskManagerService taskManagerService,
      FeatureSourceRepository featureSourceRepository,
      ApplicationRepository applicationRepository,
      ConfigurationRepository configurationRepository,
      FeatureSourceFactoryHelper featureSourceFactoryHelper,
      SearchIndexRepository searchIndexRepository,
      UploadRepository uploadRepository,
      PageRepository pageRepository,
      JdbcClient jdbcClient) {
    this.appContext = appContext;
    this.userRepository = userRepository;
    this.groupRepository = groupRepository;
    this.catalogRepository = catalogRepository;
    this.geoServiceRepository = geoServiceRepository;
    this.geoServiceHelper = geoServiceHelper;
    this.solrService = solrService;
    this.taskManagerService = taskManagerService;
    this.featureSourceRepository = featureSourceRepository;
    this.applicationRepository = applicationRepository;
    this.configurationRepository = configurationRepository;
    this.featureSourceFactoryHelper = featureSourceFactoryHelper;
    this.searchIndexRepository = searchIndexRepository;
    this.uploadRepository = uploadRepository;
    this.pageRepository = pageRepository;
    this.jdbcClient = jdbcClient;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void populate() throws Exception {
    InternalAdminAuthentication.setInSecurityContext();
    try {
      // Used in conjunction with tailormap-api.database.clean=true so the database has been cleaned
      // and the latest schema re-created
      createTestUsersAndGroups();
      createConfigurationTestData();
      if (categories.contains("catalog")) {
        createCatalogTestData();
      }
      if (categories.contains("apps")) {
        createAppTestData();
      }
      if (categories.contains("search-index")) {
        try {
          createSolrIndex();
        } catch (Exception e) {
          logger.error("Exception creating Solr Index for testdata (continuing)", e);
        }
      }
      if (categories.contains("tasks")) {
        createScheduledTasks();
      }
      if (categories.contains("pages")) {
        createPages();
      }
      logger.info("Test entities created");
      if (categories.contains("drawing")) {
        insertTestDrawing();
        logger.info("Test drawing created");
        try {
          insertDrawingStyle();
          logger.info("Test drawing style created");
        } catch (Exception e) {
          logger.error("Exception creating drawing style", e);
        }
      }
    } finally {
      InternalAdminAuthentication.clearSecurityContextAuthentication();
    }
    if (exit) {
      // Exit after transaction is completed - for 'mvn verify' to populate testdata before
      // integration tests
      new Thread(() -> {
            try {
              logger.info("Exiting in 10 seconds");
              Thread.sleep(10000);
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
    groupBar.addOrUpdateAdminProperty("group-property", true, true);
    groupBar.addOrUpdateAdminProperty("group-private-property", 999.9, false);
    groupRepository.save(groupBar);

    Group groupBaz = new Group().setName("test-baz").setDescription("Used for integration tests.");
    groupRepository.save(groupBaz);

    // Normal user
    User u = new User().setUsername("user").setPassword("{noop}user").setEmail("user@example.com");
    u.getGroups().addAll(List.of(groupFoo, groupBar, groupBaz));
    userRepository.save(u);

    // Foo only user
    u = new User()
        .setUsername("foo")
        .setPassword("{noop}foo")
        .setEmail("foo@example.com")
        .setName("Foo only user");
    u.getGroups().add(groupFoo);
    userRepository.save(u);

    // Superuser with all access
    u = new User().setUsername("tm-admin").setPassword(adminHashedPassword);
    u.addOrUpdateAdminProperty("some-property", "some-value", true);
    u.addOrUpdateAdminProperty("admin-property", "private-value", false);
    u.getGroups().add(groupRepository.findById(Group.ADMIN).orElseThrow());
    u.getGroups().add(groupBar);
    userRepository.save(u);
  }

  private final List<AuthorizationRule> ruleAnonymousRead = List.of(new AuthorizationRule()
      .groupName(Group.ANONYMOUS)
      .decisions(Map.of(ACCESS_TYPE_VIEW, AuthorizationRuleDecision.ALLOW)));

  private final List<AuthorizationRule> ruleLoggedIn = List.of(new AuthorizationRule()
      .groupName(Group.AUTHENTICATED)
      .decisions(Map.of(ACCESS_TYPE_VIEW, AuthorizationRuleDecision.ALLOW)));

  @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
  private void createCatalogTestData() throws Exception {
    Catalog catalog = catalogRepository.findById(Catalog.MAIN).orElseThrow();
    CatalogNode rootCatalogNode = catalog.getNodes().get(0);
    CatalogNode catalogNode = new CatalogNode().id("test").title("Test services");
    rootCatalogNode.addChildrenItem(catalogNode.getId());
    catalog.getNodes().add(catalogNode);

    String osmAttribution = "© [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors";

    Bounds rdTileGridExtent =
        new Bounds().minx(-285401.92).maxx(595401.92).miny(22598.08).maxy(903401.92);

    Upload legend = new Upload()
        .setCategory(Upload.CATEGORY_LEGEND)
        .setFilename("gemeentegebied-legend.png")
        .setMimeType("image/png")
        .setContent(new ClassPathResource("test/gemeentegebied-legend.png").getContentAsByteArray())
        .setLastModified(OffsetDateTime.now(ZoneId.systemDefault()));
    uploadRepository.save(legend);

    Collection<GeoService> services = List.of(
        new GeoService()
            .setId("demo")
            .setProtocol(WMS)
            .setTitle("Demo")
            .setPublished(true)
            .setAuthorizationRules(ruleAnonymousRead)
            .setUrl("https://demo.tailormap.com/geoserver/geodata/ows?SERVICE=WMS"),
        new GeoService()
            .setId("osm")
            .setProtocol(XYZ)
            .setTitle("OSM")
            .setUrl("https://tile.openstreetmap.org/{z}/{x}/{y}.png")
            .setAuthorizationRules(ruleAnonymousRead)
            .setSettings(new GeoServiceSettings()
                .xyzCrs("EPSG:3857")
                .layerSettings(Map.of(
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
            .setAuthorizationRules(ruleAnonymousRead)
            .setPublished(true),
        new GeoService()
            .setId("filtered-snapshot-geoserver")
            .setProtocol(WMS)
            .setTitle("Test GeoServer (with authorization rules)")
            .setUrl("https://snapshot.tailormap.nl/geoserver/wms")
            .setAuthorizationRules(List.of(
                new AuthorizationRule()
                    .groupName("test-foo")
                    .decisions(Map.of(ACCESS_TYPE_VIEW, AuthorizationRuleDecision.ALLOW)),
                new AuthorizationRule()
                    .groupName("test-baz")
                    .decisions(Map.of(ACCESS_TYPE_VIEW, AuthorizationRuleDecision.ALLOW))))
            .setSettings(new GeoServiceSettings()
                .layerSettings(Map.of(
                    "BGT",
                    new GeoServiceLayerSettings()
                        .addAuthorizationRulesItem(new AuthorizationRule()
                            .groupName("test-foo")
                            .decisions(Map.of(
                                ACCESS_TYPE_VIEW, AuthorizationRuleDecision.DENY)))
                        .addAuthorizationRulesItem(new AuthorizationRule()
                            .groupName("test-baz")
                            .decisions(Map.of(
                                ACCESS_TYPE_VIEW, AuthorizationRuleDecision.ALLOW))))))
            .setPublished(true),
        new GeoService()
            .setId("snapshot-geoserver-proxied")
            .setProtocol(WMS)
            .setTitle("Test GeoServer (proxied)")
            .setUrl("https://snapshot.tailormap.nl/geoserver/wms")
            .setAuthorizationRules(ruleAnonymousRead)
            .setSettings(new GeoServiceSettings().useProxy(true)),
        new GeoService()
            .setId("openbasiskaart")
            .setProtocol(WMTS)
            .setTitle("Openbasiskaart")
            .setUrl("https://www.openbasiskaart.nl/mapcache/wmts")
            .setAuthorizationRules(ruleAnonymousRead)
            .setSettings(new GeoServiceSettings()
                .defaultLayerSettings(new GeoServiceDefaultLayerSettings().attribution(osmAttribution))
                .layerSettings(Map.of(
                    "osm",
                    new GeoServiceLayerSettings()
                        .title("Openbasiskaart")
                        .hiDpiDisabled(false)
                        .hiDpiMode(TileLayerHiDpiMode.SUBSTITUTELAYERSHOWNEXTZOOMLEVEL)
                        .hiDpiSubstituteLayer("osm-hq")))),
        new GeoService()
            .setId("openbasiskaart-proxied")
            .setProtocol(WMTS)
            .setTitle("Openbasiskaart (proxied)")
            .setUrl("https://www.openbasiskaart.nl/mapcache/wmts")
            .setAuthorizationRules(ruleAnonymousRead)
            // The service actually doesn't require authentication, but also doesn't mind it
            // Just for testing
            .setAuthentication(new ServiceAuthentication()
                .method(ServiceAuthentication.MethodEnum.PASSWORD)
                .username("test")
                .password("test"))
            .setSettings(new GeoServiceSettings()
                .useProxy(true)
                .defaultLayerSettings(new GeoServiceDefaultLayerSettings().attribution(osmAttribution))
                .layerSettings(Map.of(
                    "osm",
                    new GeoServiceLayerSettings()
                        .hiDpiDisabled(false)
                        .hiDpiMode(TileLayerHiDpiMode.SUBSTITUTELAYERSHOWNEXTZOOMLEVEL)
                        .hiDpiSubstituteLayer("osm-hq")))),
        new GeoService()
            .setId("openbasiskaart-tms")
            .setProtocol(XYZ)
            .setTitle("Openbasiskaart (TMS)")
            .setUrl("https://openbasiskaart.nl/mapcache/tms/1.0.0/osm@rd/{z}/{x}/{-y}.png")
            .setAuthorizationRules(ruleAnonymousRead)
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
                                .hiDpiDisabled(false)
                                .hiDpiMode(
                                    TileLayerHiDpiMode
                                        .SUBSTITUTELAYERTILEPIXELRATIOONLY)
                                .hiDpiSubstituteLayer(
                                    "https://openbasiskaart.nl/mapcache/tms/1.0.0/osm-hq@rd-hq/{z}/{x}/{-y}.png")))),
        new GeoService()
            .setId("pdok-hwh-luchtfotorgb")
            .setProtocol(WMTS)
            .setTitle("PDOK HWH luchtfoto")
            .setUrl("https://service.pdok.nl/hwh/luchtfotorgb/wmts/v1_0")
            .setAuthorizationRules(ruleAnonymousRead)
            .setPublished(true)
            .setSettings(new GeoServiceSettings()
                .defaultLayerSettings(new GeoServiceDefaultLayerSettings()
                    .attribution("© [Beeldmateriaal.nl](https://beeldmateriaal.nl)")
                    .hiDpiDisabled(false))
                .putLayerSettingsItem(
                    "Actueel_orthoHR", new GeoServiceLayerSettings().title("Luchtfoto"))),
        new GeoService()
            .setId("b3p-mapproxy-luchtfoto")
            .setProtocol(XYZ)
            .setTitle("Luchtfoto (TMS)")
            .setUrl("https://mapproxy.b3p.nl/tms/1.0.0/luchtfoto/EPSG28992/{z}/{x}/{-y}.jpeg")
            .setAuthorizationRules(ruleAnonymousRead)
            .setPublished(true)
            .setSettings(new GeoServiceSettings()
                .xyzCrs("EPSG:28992")
                .defaultLayerSettings(new GeoServiceDefaultLayerSettings()
                    .attribution("© [Beeldmateriaal.nl](https://beeldmateriaal.nl)")
                    .hiDpiDisabled(false))
                .layerSettings(Map.of(
                    "xyz",
                    new GeoServiceLayerSettings()
                        .maxZoom(14)
                        .tileGridExtent(rdTileGridExtent)
                        .hiDpiMode(TileLayerHiDpiMode.SHOWNEXTZOOMLEVEL)))),
        new GeoService()
            .setId("at-basemap")
            .setProtocol(WMTS)
            .setTitle("basemap.at")
            .setUrl("https://mapsneu.wien.gv.at/basemapneu/1.0.0/WMTSCapabilities.xml")
            .setAuthorizationRules(ruleAnonymousRead)
            .setPublished(true)
            .setSettings(new GeoServiceSettings()
                .defaultLayerSettings(new GeoServiceDefaultLayerSettings()
                    .attribution("© [basemap.at](https://basemap.at)")
                    .hiDpiDisabled(true))
                .layerSettings(Map.of(
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
            .setUrl("https://service.pdok.nl/kadaster/bestuurlijkegebieden/wms/v1_0?service=WMS")
            .setAuthorizationRules(ruleAnonymousRead)
            .setSettings(new GeoServiceSettings()
                .defaultLayerSettings(new GeoServiceDefaultLayerSettings()
                    .description("This layer shows an administrative boundary."))
                // No attribution required: service is CC0
                .serverType(GeoServiceSettings.ServerTypeEnum.MAPSERVER)
                .useProxy(true)
                .putLayerSettingsItem(
                    "Gemeentegebied",
                    new GeoServiceLayerSettings()
                        .legendImageId(legend.getId().toString())))
            .setPublished(true)
            .setTitle("PDOK Kadaster bestuurlijke gebieden"),
        new GeoService()
            .setId("bestuurlijkegebieden-proxied")
            .setProtocol(WMS)
            .setUrl("https://service.pdok.nl/kadaster/bestuurlijkegebieden/wms/v1_0?service=WMS")
            .setAuthorizationRules(ruleAnonymousRead)
            // The service actually doesn't require authentication, but also doesn't mind it
            // Just for testing that proxied services with auth are not available in public
            // apps (even when logged in), in any controllers (map, proxy, features)
            .setAuthentication(new ServiceAuthentication()
                .method(ServiceAuthentication.MethodEnum.PASSWORD)
                .username("test")
                .password("test"))
            .setSettings(new GeoServiceSettings()
                // No attribution required: service is CC0
                .serverType(GeoServiceSettings.ServerTypeEnum.MAPSERVER)
                .useProxy(true))
            .setPublished(true)
            .setTitle("Bestuurlijke gebieden (proxied met auth)"),
        new GeoService()
            .setId("3dbag_utrecht")
            .setProtocol(TILES3D)
            .setUrl("https://3dtilesnederland.nl/tiles/1.0/implicit/nederland/344.json")
            .setTitle("3D BAG Utrecht")
            .setPublished(true)
            .setAuthorizationRules(ruleAnonymousRead),
        new GeoService()
            .setId("ahn_terrain_model")
            .setProtocol(QUANTIZEDMESH)
            .setUrl(
                "https://api.pdok.nl/kadaster/3d-basisvoorziening/ogc/v1/collections/digitaalterreinmodel")
            .setTitle("AHN Terrain Model")
            .setPublished(true)
            .setAuthorizationRules(ruleAnonymousRead),
        new GeoService()
            .setId("3d_basisvoorziening_gebouwen_proxy")
            .setProtocol(TILES3D)
            .setUrl(
                "https://api.pdok.nl/kadaster/3d-basisvoorziening/ogc/v1_0/collections/gebouwen/3dtiles")
            .setTitle("3D Basisvoorziening Gebouwen Proxy")
            .setPublished(true)
            .setAuthorizationRules(ruleAnonymousRead)
            .setSettings(new GeoServiceSettings().useProxy(true)),
        new GeoService()
            .setId("3d_utrecht_proxied_auth")
            .setProtocol(TILES3D)
            .setUrl("https://snapshot.tailormap.nl/tiles3d-proxy/3dtiles")
            .setTitle("3D Utrecht Proxied with Authorization")
            .setPublished(true)
            .setAuthorizationRules(ruleAnonymousRead)
            .setAuthentication(new ServiceAuthentication()
                .method(ServiceAuthentication.MethodEnum.PASSWORD)
                .username(tiles3dProxyUsername)
                .password(tiles3dProxyPassword))
            .setSettings(new GeoServiceSettings().useProxy(true))
        // TODO MapServer WMS "https://wms.geonorge.no/skwms1/wms.adm_enheter_historisk"
        );

    if (map5url != null) {
      GeoServiceLayerSettings osmAttr = new GeoServiceLayerSettings().attribution(osmAttribution);
      GeoServiceLayerSettings map5Attr = new GeoServiceLayerSettings()
          .attribution("Kaarten: [Map5.nl](https://map5.nl), data: " + osmAttribution);
      services = new ArrayList<>(services);
      services.add(new GeoService()
          .setId("map5")
          .setProtocol(WMTS)
          .setTitle("Map5")
          .setUrl(map5url)
          .setAuthorizationRules(ruleAnonymousRead)
          .setSettings(new GeoServiceSettings()
              .defaultLayerSettings(new GeoServiceDefaultLayerSettings().hiDpiDisabled(true))
              .layerSettings(Map.of(
                  "openlufo",
                  new GeoServiceLayerSettings()
                      .attribution("© [Beeldmateriaal.nl](https://beeldmateriaal.nl), "
                          + osmAttribution),
                  "luforoadslabels",
                  osmAttr,
                  "map5topo",
                  new GeoServiceLayerSettings()
                      .attribution(map5Attr.getAttribution())
                      .hiDpiDisabled(false)
                      .hiDpiMode(TileLayerHiDpiMode.SUBSTITUTELAYERSHOWNEXTZOOMLEVEL)
                      .hiDpiSubstituteLayer("map5topo_hq"),
                  "map5topo_gray",
                  map5Attr,
                  "map5topo_simple",
                  map5Attr,
                  "map5topo_simple_gray",
                  map5Attr,
                  "opensimpletopo",
                  osmAttr,
                  "opensimpletopo_gray",
                  osmAttr,
                  "opentopo",
                  osmAttr,
                  "opentopo_gray",
                  osmAttr))));
    }

    for (GeoService geoService : services) {
      try {
        geoServiceHelper.loadServiceCapabilities(geoService);
      } catch (Exception e) {
        logger.error(
            "Error loading capabilities for {} service URL {}: {}, {}",
            geoService.getProtocol().getValue(),
            geoService.getUrl(),
            e.getClass(),
            e.getMessage());
      }

      geoServiceRepository.save(geoService);
      catalogNode.addItemsItem(new TailormapObjectRef()
          .kind(TailormapObjectRef.KindEnum.GEO_SERVICE)
          .id(geoService.getId()));
    }

    CatalogNode wfsFeatureSourceCatalogNode =
        new CatalogNode().id("wfs_feature_sources").title("WFS feature sources");
    rootCatalogNode.addChildrenItem(wfsFeatureSourceCatalogNode.getId());
    catalog.getNodes().add(wfsFeatureSourceCatalogNode);

    services.stream().filter(s -> s.getProtocol() == WMS).forEach(s -> {
      geoServiceHelper.findAndSaveRelatedWFS(s);
      List<TMFeatureSource> linkedSources = featureSourceRepository.findByLinkedServiceId(s.getId());
      for (TMFeatureSource linkedSource : linkedSources) {
        wfsFeatureSourceCatalogNode.addItemsItem(new TailormapObjectRef()
            .kind(TailormapObjectRef.KindEnum.FEATURE_SOURCE)
            .id(linkedSource.getId().toString()));
      }
    });

    String geodataPassword = "980f1c8A-25933b2";

    Map<String, TMFeatureSource> featureSources = Map.of(
        "postgis",
        new TMFeatureSource()
            .setProtocol(TMFeatureSource.Protocol.JDBC)
            .setTitle("PostGIS")
            .setJdbcConnection(new JDBCConnectionProperties()
                .dbtype(JDBCConnectionProperties.DbtypeEnum.POSTGIS)
                .host(connectToSpatialDbsAtLocalhost ? "127.0.0.1" : "postgis")
                .port(connectToSpatialDbsAtLocalhost ? 54322 : 5432)
                .database("geodata")
                .schema("public")
                .additionalProperties(Map.of("connectionOptions", "?ApplicationName=tailormap-api")))
            .setAuthentication(new ServiceAuthentication()
                .method(ServiceAuthentication.MethodEnum.PASSWORD)
                .username("geodata")
                .password(geodataPassword)),
        "postgis_osm",
        new TMFeatureSource()
            .setProtocol(TMFeatureSource.Protocol.JDBC)
            .setTitle("PostGIS OSM")
            .setJdbcConnection(new JDBCConnectionProperties()
                .dbtype(JDBCConnectionProperties.DbtypeEnum.POSTGIS)
                .host(connectToSpatialDbsAtLocalhost ? "127.0.0.1" : "postgis")
                .port(connectToSpatialDbsAtLocalhost ? 54322 : 5432)
                .database("geodata")
                .schema("osm")
                .additionalProperties(Map.of("connectionOptions", "?ApplicationName=tailormap-api")))
            .setAuthentication(new ServiceAuthentication()
                .method(ServiceAuthentication.MethodEnum.PASSWORD)
                .username("geodata")
                .password(geodataPassword)),
        "oracle",
        new TMFeatureSource()
            .setProtocol(TMFeatureSource.Protocol.JDBC)
            .setTitle("Oracle")
            .setJdbcConnection(new JDBCConnectionProperties()
                .dbtype(JDBCConnectionProperties.DbtypeEnum.ORACLE)
                .host(connectToSpatialDbsAtLocalhost ? "127.0.0.1" : "oracle")
                .database("/FREEPDB1")
                .schema("GEODATA")
                .additionalProperties(Map.of("connectionOptions", "?oracle.jdbc.J2EE13Compliant=true")))
            .setAuthentication(new ServiceAuthentication()
                .method(ServiceAuthentication.MethodEnum.PASSWORD)
                .username("geodata")
                .password(geodataPassword)),
        "sqlserver",
        new TMFeatureSource()
            .setProtocol(TMFeatureSource.Protocol.JDBC)
            .setTitle("MS SQL Server")
            .setJdbcConnection(new JDBCConnectionProperties()
                .dbtype(JDBCConnectionProperties.DbtypeEnum.SQLSERVER)
                .host(connectToSpatialDbsAtLocalhost ? "127.0.0.1" : "sqlserver")
                .database("geodata")
                .schema("dbo")
                .additionalProperties(Map.of("connectionOptions", ";encrypt=false")))
            .setAuthentication(new ServiceAuthentication()
                .method(ServiceAuthentication.MethodEnum.PASSWORD)
                .username("geodata")
                .password(geodataPassword)),
        "pdok-kadaster-bestuurlijkegebieden",
        new TMFeatureSource()
            .setProtocol(TMFeatureSource.Protocol.WFS)
            .setUrl(
                "https://service.pdok.nl/kadaster/bestuurlijkegebieden/wfs/v1_0?service=WFS&VERSION=2.0.0")
            .setTitle("Bestuurlijke gebieden")
            .setNotes(
                "Overzicht van de bestuurlijke indeling van Nederland in gemeenten en provincies alsmede de rijksgrens. Gegevens zijn afgeleid uit de Basisregistratie Kadaster (BRK)."));
    featureSourceRepository.saveAll(featureSources.values());

    new WFSFeatureSourceHelper().loadCapabilities(featureSources.get("pdok-kadaster-bestuurlijkegebieden"));
    geoServiceRepository.findById("pdok-kadaster-bestuurlijkegebieden").ifPresent(geoService -> {
      geoService
          .getSettings()
          .getLayerSettings()
          .put(
              "Provinciegebied",
              new GeoServiceLayerSettings()
                  .description("The administrative boundary of Dutch Provinces, connected to a WFS.")
                  .featureType(new FeatureTypeRef()
                      .featureSourceId(featureSources
                          .get("pdok-kadaster-bestuurlijkegebieden")
                          .getId())
                      .featureTypeName("bestuurlijkegebieden:Provinciegebied"))
                  .title("Provinciegebied (WFS)"));
      geoServiceRepository.save(geoService);
    });

    geoServiceRepository.findById("bestuurlijkegebieden-proxied").ifPresent(geoService -> {
      geoService
          .getSettings()
          .getLayerSettings()
          .put(
              "Provinciegebied",
              new GeoServiceLayerSettings()
                  .featureType(new FeatureTypeRef()
                      .featureSourceId(featureSources
                          .get("pdok-kadaster-bestuurlijkegebieden")
                          .getId())
                      .featureTypeName("bestuurlijkegebieden:Provinciegebied"))
                  .title("Provinciegebied (WFS, proxied met auth)"));
      geoServiceRepository.save(geoService);
    });

    CatalogNode featureSourceCatalogNode =
        new CatalogNode().id("feature_sources").title("Test feature sources");
    rootCatalogNode.addChildrenItem(featureSourceCatalogNode.getId());
    catalog.getNodes().add(featureSourceCatalogNode);

    for (TMFeatureSource featureSource : featureSources.values()) {
      featureSourceCatalogNode.addItemsItem(new TailormapObjectRef()
          .kind(TailormapObjectRef.KindEnum.FEATURE_SOURCE)
          .id(featureSource.getId().toString()));
    }
    catalogRepository.save(catalog);

    if (connectToSpatialDbs) {
      featureSources.values().forEach(fs -> {
        try {
          if (fs.getProtocol() == TMFeatureSource.Protocol.JDBC) {
            new JDBCFeatureSourceHelper().loadCapabilities(fs);
          } else if (fs.getProtocol() == TMFeatureSource.Protocol.WFS) {
            new WFSFeatureSourceHelper().loadCapabilities(fs);
          }
        } catch (Exception e) {
          logger.error("Error loading capabilities for feature source {}", fs.getTitle(), e);
        }
      });

      services.stream()
          // Set layer settings for both the proxied and non-proxied one, but don't overwrite the
          // authorization rules for the "filtered-snapshot-geoserver" service
          .filter(s -> s.getId().startsWith("snapshot-geoserver"))
          .forEach(s -> s.getSettings()
              .layerSettings(Map.of(
                  "postgis:begroeidterreindeel",
                  new GeoServiceLayerSettings()
                      .description(
                          """
This layer shows data from https://www.postgis.net/

https://postgis.net/brand.svg""")
                      .featureType(new FeatureTypeRef()
                          .featureSourceId(featureSources
                              .get("postgis")
                              .getId())
                          .featureTypeName("begroeidterreindeel")),
                  "postgis:bak",
                  new GeoServiceLayerSettings()
                      .featureType(new FeatureTypeRef()
                          .featureSourceId(featureSources
                              .get("postgis")
                              .getId())
                          .featureTypeName("bak")),
                  "postgis:kadastraal_perceel",
                  new GeoServiceLayerSettings()
                      .description("cadastral parcel label points")
                      .featureType(new FeatureTypeRef()
                          .featureSourceId(featureSources
                              .get("postgis")
                              .getId())
                          .featureTypeName("kadastraal_perceel")),
                  "sqlserver:wegdeel",
                  new GeoServiceLayerSettings()
                      .attribution(
                          "CC BY 4.0 [BGT/Kadaster](https://www.nationaalgeoregister.nl/geonetwork/srv/api/records/2cb4769c-b56e-48fa-8685-c48f61b9a319)")
                      .description(
                          """
This layer shows data from [MS SQL Server](https://learn.microsoft.com/en-us/sql/relational-databases/spatial/spatial-data-sql-server).

https://social.technet.microsoft.com/wiki/cfs-filesystemfile.ashx/__key/communityserver-components-imagefileviewer/communityserver-wikis-components-files-00-00-00-00-05/1884.SQL_5F00_h_5F00_rgb.png_2D00_550x0.png""")
                      .featureType(new FeatureTypeRef()
                          .featureSourceId(featureSources
                              .get("sqlserver")
                              .getId())
                          .featureTypeName("wegdeel")),
                  "oracle:WATERDEEL",
                  new GeoServiceLayerSettings()
                      .description("This layer shows data from Oracle Spatial.")
                      .featureType(new FeatureTypeRef()
                          .featureSourceId(featureSources
                              .get("oracle")
                              .getId())
                          .featureTypeName("WATERDEEL")),
                  "postgis:osm_polygon",
                  new GeoServiceLayerSettings()
                      .description("This layer shows OSM data from postgis.")
                      .featureType(new FeatureTypeRef()
                          .featureSourceId(featureSources
                              .get("postgis_osm")
                              .getId())
                          .featureTypeName("osm_polygon")))));
    }

    featureSources.get("pdok-kadaster-bestuurlijkegebieden").getFeatureTypes().stream()
        .filter(ft -> ft.getName().equals("bestuurlijkegebieden:Provinciegebied"))
        .findFirst()
        .ifPresent(ft -> {
          ft.getSettings().addHideAttributesItem("identificatie");
          ft.getSettings().addHideAttributesItem("ligtInLandCode");
          ft.getSettings().addHideAttributesItem("fuuid");
          ft.getSettings().putAttributeSettingsItem("naam", new AttributeSettings().title("Naam"));
          ft.getSettings()
              .setTemplate(
                  new FeatureTypeTemplate()
                      .templateLanguage("simple")
                      .markupLanguage("markdown")
                      .template(
                          """
### Provincie
Deze provincie heet **{{naam}}** en ligt in _{{ligtInLandNaam}}_.

| Attribuut | Waarde             |
| --------- | ------------------ |
| `code`    | {{code}}           |
| `naam`    | {{naam}}           |
| `ligt in` | {{ligtInLandNaam}} |"""));
        });

    featureSources.get("postgis").getFeatureTypes().stream()
        .filter(ft -> ft.getName().equals("begroeidterreindeel"))
        .findFirst()
        .ifPresent(ft -> {
          ft.getSettings().addHideAttributesItem("terminationdate");
          ft.getSettings().addHideAttributesItem("geom_kruinlijn");
          ft.getSettings().putAttributeSettingsItem("gmlid", new AttributeSettings().title("GML ID"));
          ft.getSettings()
              .putAttributeSettingsItem("identificatie", new AttributeSettings().title("Identificatie"));
          ft.getSettings()
              .putAttributeSettingsItem(
                  "tijdstipregistratie", new AttributeSettings().title("Registratie"));
          ft.getSettings()
              .putAttributeSettingsItem(
                  "eindregistratie", new AttributeSettings().title("Eind registratie"));
          ft.getSettings().putAttributeSettingsItem("class", new AttributeSettings().title("Klasse"));
          ft.getSettings()
              .putAttributeSettingsItem("bronhouder", new AttributeSettings().title("Bronhouder"));
          ft.getSettings()
              .putAttributeSettingsItem("inonderzoek", new AttributeSettings().title("In onderzoek"));
          ft.getSettings()
              .putAttributeSettingsItem(
                  "relatievehoogteligging", new AttributeSettings().title("Relatieve hoogteligging"));
          ft.getSettings()
              .putAttributeSettingsItem("bgt_status", new AttributeSettings().title("BGT status"));
          ft.getSettings()
              .putAttributeSettingsItem("plus_status", new AttributeSettings().title("Plus-status"));
          ft.getSettings()
              .putAttributeSettingsItem(
                  "plus_fysiekvoorkomen", new AttributeSettings().title("Plus-fysiek voorkomen"));
          ft.getSettings()
              .putAttributeSettingsItem(
                  "begroeidterreindeeloptalud", new AttributeSettings().title("Op talud"));
          ft.getSettings().addAttributeOrderItem("identificatie");
          ft.getSettings().addAttributeOrderItem("bronhouder");
          ft.getSettings().addAttributeOrderItem("class");
        });

    featureSources.get("postgis").getFeatureTypes().stream()
        .filter(ft -> ft.getName().equals("bak"))
        .findFirst()
        .ifPresent(ft -> {
          ft.getSettings().addHideAttributesItem("gmlid");
          ft.getSettings().addHideAttributesItem("lv_publicatiedatum");
          ft.getSettings().addHideAttributesItem("creationdate");
          ft.getSettings().addHideAttributesItem("tijdstipregistratie");
          ft.getSettings().addHideAttributesItem("eindregistratie");
          ft.getSettings().addHideAttributesItem("terminationdate");
          ft.getSettings().addHideAttributesItem("inonderzoek");
          ft.getSettings().addHideAttributesItem("relatievehoogteligging");
          ft.getSettings().addHideAttributesItem("bgt_status");
          ft.getSettings().addHideAttributesItem("plus_status");
          ft.getSettings().addHideAttributesItem("function_");
          ft.getSettings().addHideAttributesItem("plus_type");
        });

    featureSources.get("postgis").getFeatureTypes().stream()
        .filter(ft -> ft.getName().equals("kadastraal_perceel"))
        .findFirst()
        .ifPresent(ft -> ft.getSettings().addHideAttributesItem("gml_id"));
  }

  public void createAppTestData() throws Exception {
    Upload logo = new Upload()
        .setCategory(Upload.CATEGORY_APP_LOGO)
        .setFilename("gradient.svg")
        .setMimeType("image/svg+xml")
        .setContent(new ClassPathResource("test/gradient-logo.svg").getContentAsByteArray())
        .setLastModified(OffsetDateTime.now(ZoneId.systemDefault()));
    uploadRepository.save(logo);

    List<AppTreeNode> baseNodes = List.of(
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

    Application app = new Application()
        .setName("default")
        .setTitle("Tailormap demo")
        .setCrs("EPSG:28992")
        .setAuthorizationRules(ruleAnonymousRead)
        .setComponents(List.of(
            new Component()
                .type("SIMPLE_SEARCH")
                .config(new ComponentConfig()
                    .enabled(true)
                    .putAdditionalProperty("municipalities", List.of("0344"))),
            new Component().type("EDIT").config(new ComponentConfig().enabled(true)),
            new Component()
                .type("COORDINATE_LINK_WINDOW")
                .config(new ComponentConfig()
                    .enabled(true)
                    .putAdditionalProperty(
                        "urls",
                        List.of(
                            Map.of(
                                "id",
                                "google-maps",
                                "url",
                                "https://www.google.com/maps/@[lat],[lon],18z",
                                "alias",
                                "Google Maps",
                                "projection",
                                "EPSG:4326"),
                            Map.of(
                                "id",
                                "tm-demo",
                                "url",
                                "https://demo.tailormap.com/#@[X],[Y],18",
                                "alias",
                                "Tailormap demo",
                                "projection",
                                "EPSG:28992"))))))
        .setContentRoot(new AppContent()
            .addBaseLayerNodesItem(new AppTreeLevelNode()
                .objectType("AppTreeLevelNode")
                .id("root-base-layers")
                .root(true)
                .title("Base layers")
                .childrenIds(List.of(
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
            .addBaseLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:openbasiskaart-tms:xyz")
                .serviceId("openbasiskaart-tms")
                .layerName("xyz")
                .visible(false))
            .addBaseLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:b3p-mapproxy-luchtfoto:xyz")
                .serviceId("b3p-mapproxy-luchtfoto")
                .layerName("xyz")
                .visible(false))
            .addLayerNodesItem(new AppTreeLevelNode()
                .objectType("AppTreeLevelNode")
                .id("root")
                .root(true)
                .title("Layers")
                .childrenIds(List.of(
                    "lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied",
                    "lyr:bestuurlijkegebieden-proxied:Provinciegebied",
                    "lyr:pdok-kadaster-bestuurlijkegebieden:Gemeentegebied",
                    "lyr:snapshot-geoserver:postgis:begroeidterreindeel",
                    "lyr:snapshot-geoserver:postgis:bak",
                    "lyr:snapshot-geoserver:postgis:kadastraal_perceel",
                    "lyr:snapshot-geoserver:sqlserver:wegdeel",
                    "lyr:snapshot-geoserver:oracle:WATERDEEL",
                    "lyr:snapshot-geoserver:BGT",
                    "lvl:proxied",
                    "lvl:osm",
                    "lvl:archeo")))
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied")
                .serviceId("pdok-kadaster-bestuurlijkegebieden")
                .layerName("Provinciegebied")
                .visible(true))
            // This is a layer from proxied service with auth that should also not be
            // visible, but it has a feature source attached, should also be denied for
            // features access and not be included in TOC
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:bestuurlijkegebieden-proxied:Provinciegebied")
                .serviceId("bestuurlijkegebieden-proxied")
                .layerName("Provinciegebied")
                .visible(false))
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:pdok-kadaster-bestuurlijkegebieden:Gemeentegebied")
                .serviceId("pdok-kadaster-bestuurlijkegebieden")
                .layerName("Gemeentegebied")
                .visible(true))
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:snapshot-geoserver:postgis:begroeidterreindeel")
                .serviceId("snapshot-geoserver")
                .layerName("postgis:begroeidterreindeel")
                .visible(true))
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:snapshot-geoserver:postgis:bak")
                .serviceId("snapshot-geoserver")
                .layerName("postgis:bak")
                .visible(false))
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:snapshot-geoserver:postgis:kadastraal_perceel")
                .serviceId("snapshot-geoserver")
                .layerName("postgis:kadastraal_perceel")
                .visible(false))
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:snapshot-geoserver:sqlserver:wegdeel")
                .serviceId("snapshot-geoserver")
                .layerName("sqlserver:wegdeel")
                .visible(true))
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:snapshot-geoserver:oracle:WATERDEEL")
                .serviceId("snapshot-geoserver")
                .layerName("oracle:WATERDEEL")
                .visible(true))
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:snapshot-geoserver:BGT")
                .serviceId("snapshot-geoserver")
                .layerName("BGT")
                .visible(false))
            .addLayerNodesItem(new AppTreeLevelNode()
                .objectType("AppTreeLevelNode")
                .id("lvl:proxied")
                .title("Proxied")
                .childrenIds(List.of("lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel")))
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel")
                .serviceId("snapshot-geoserver-proxied")
                .layerName("postgis:begroeidterreindeel")
                .visible(false))
            .addLayerNodesItem(new AppTreeLevelNode()
                .objectType("AppTreeLevelNode")
                .id("lvl:osm")
                .title("OSM")
                .childrenIds(List.of("lyr:snapshot-geoserver:postgis:osm_polygon")))
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:snapshot-geoserver:postgis:osm_polygon")
                .serviceId("snapshot-geoserver")
                .layerName("postgis:osm_polygon")
                .visible(false))
            .addLayerNodesItem(new AppTreeLevelNode()
                .objectType("AppTreeLevelNode")
                .id("lvl:archeo")
                .title("Archeology")
                .childrenIds(List.of("lyr:demo:geomorfologie")))
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:demo:geomorfologie")
                .serviceId("demo")
                .layerName("geomorfologie")
                .visible(true)))
        .setStyling(new AppStyling().logo(logo.getId().toString()))
        .setSettings(new AppSettings()
            .putLayerSettingsItem("lyr:openbasiskaart:osm", new AppLayerSettings().title("Openbasiskaart"))
            .putLayerSettingsItem(
                "lyr:pdok-hwh-luchtfotorgb:Actueel_orthoHR", new AppLayerSettings().title("Luchtfoto"))
            .putLayerSettingsItem(
                "lyr:openbasiskaart-proxied:osm",
                new AppLayerSettings().title("Openbasiskaart (proxied)"))
            .putLayerSettingsItem(
                "lyr:snapshot-geoserver:oracle:WATERDEEL",
                new AppLayerSettings()
                    .opacity(50)
                    .title("Waterdeel overridden title")
                    .editable(true)
                    .description("This is the layer description from the app layer setting.")
                    .attribution(
                        "CC BY 4.0 [BGT/Kadaster](https://www.nationaalgeoregister.nl/geonetwork/srv/api/records/2cb4769c-b56e-48fa-8685-c48f61b9a319)"))
            .putLayerSettingsItem(
                "lyr:snapshot-geoserver:postgis:osm_polygon",
                new AppLayerSettings()
                    .description("OpenStreetMap polygon data in EPSG:3857")
                    .opacity(60)
                    .editable(true)
                    .title("OSM Polygon (EPSG:3857)")
                    .attribution(
                        "© [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors"))
            .putLayerSettingsItem(
                "lyr:snapshot-geoserver:postgis:begroeidterreindeel",
                new AppLayerSettings()
                    .editable(true)
                    .addHideAttributesItem("begroeidterreindeeloptalud")
                    .addReadOnlyAttributesItem("eindregistratie"))
            .putLayerSettingsItem(
                "lyr:snapshot-geoserver:postgis:kadastraal_perceel",
                new AppLayerSettings().editable(true).addReadOnlyAttributesItem("aanduiding"))
            .putLayerSettingsItem(
                "lyr:snapshot-geoserver:sqlserver:wegdeel", new AppLayerSettings().editable(true))
            .putLayerSettingsItem(
                "lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel",
                new AppLayerSettings().editable(false))
            .putLayerSettingsItem(
                "lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied",
                new AppLayerSettings()
                    .hiddenFunctionality(Set.of(FEATURE_INFO, ATTRIBUTE_LIST, EXPORT)))
            .addFilterGroupsItem(new FilterGroup()
                .id("filtergroup1")
                .source("PRESET")
                .type(FilterGroup.TypeEnum.ATTRIBUTE)
                .layerIds(List.of("lyr:snapshot-geoserver:postgis:begroeidterreindeel"))
                .operator(FilterGroup.OperatorEnum.AND)
                .addFiltersItem(new Filter()
                    .id("filter1")
                    .type(Filter.TypeEnum.ATTRIBUTE)
                    .condition(Filter.ConditionEnum.BEFORE)
                    .addValueItem("2025-06-05")
                    .attribute("creationdate")
                    .attributeType(Filter.AttributeTypeEnum.DATE))
                .addFiltersItem(new Filter()
                    .id("filter2")
                    .type(Filter.TypeEnum.ATTRIBUTE)
                    .condition(Filter.ConditionEnum.UNIQUE_VALUES)
                    .addValueItem("bodembedekkers")
                    .addValueItem("bosplantsoen")
                    .addValueItem("gras- en kruidachtigen")
                    .attribute("plus_fysiekvoorkomen")
                    .attributeType(Filter.AttributeTypeEnum.STRING)
                    .editConfiguration(new FilterEditConfiguration()
                        .filterTool(FilterEditConfiguration.FilterToolEnum.CHECKBOX)
                        .attributeValuesSettings(List.of(
                            new AttributeValueSettings()
                                .value("bodembedekkers")
                                .initiallySelected(true)
                                .selectable(true)
                                .alias("Bodembedekkers"),
                            new AttributeValueSettings()
                                .value("bosplantsoen")
                                .initiallySelected(true)
                                .selectable(true)
                                .alias("Bosplantsoen"),
                            new AttributeValueSettings()
                                .value("gras- en kruidachtigen")
                                .initiallySelected(true)
                                .selectable(true)
                                .alias("Gras- en kruidachtigen"),
                            new AttributeValueSettings()
                                .value("griend en hakhout")
                                .initiallySelected(false)
                                .selectable(true),
                            new AttributeValueSettings()
                                .value("heesters")
                                .initiallySelected(false)
                                .selectable(true),
                            new AttributeValueSettings()
                                .value("planten")
                                .initiallySelected(false)
                                .selectable(true),
                            new AttributeValueSettings()
                                .value("struikrozen")
                                .initiallySelected(false)
                                .selectable(true),
                            new AttributeValueSettings()
                                .value("waardeOnbekend")
                                .initiallySelected(false)
                                .selectable(true))))))
            .addFilterGroupsItem(new FilterGroup()
                .id("filtergroup2")
                .source("PRESET")
                .type(FilterGroup.TypeEnum.ATTRIBUTE)
                .layerIds(List.of("lyr:snapshot-geoserver:postgis:kadastraal_perceel"))
                .operator(FilterGroup.OperatorEnum.AND)
                .addFiltersItem(new Filter()
                    .id("filter3")
                    .type(Filter.TypeEnum.ATTRIBUTE)
                    .condition(Filter.ConditionEnum.u)
                    .addValueItem("1")
                    .addValueItem("12419")
                    .attribute("perceelnummer")
                    .attributeType(Filter.AttributeTypeEnum.DOUBLE)
                    .editConfiguration(new FilterEditConfiguration()
                        .filterTool(FilterEditConfiguration.FilterToolEnum.SLIDER)
                        .initialLowerValue(1d)
                        .initialUpperValue(12419d)
                        .minimumValue(1d)
                        .maximumValue(12419d)))));

    app.getContentRoot().getBaseLayerNodes().addAll(baseNodes);
    app.setInitialExtent(
        new Bounds().minx(130011d).miny(458031d).maxx(132703d).maxy(459995d));
    app.setMaxExtent(new Bounds().minx(-285401d).miny(22598d).maxx(595401d).maxy(903401d));

    if (map5url != null) {
      AppTreeLevelNode root =
          (AppTreeLevelNode) app.getContentRoot().getBaseLayerNodes().get(0);
      List<String> childrenIds = new ArrayList<>(root.getChildrenIds());
      childrenIds.add("lyr:map5:map5topo");
      childrenIds.add("lyr:map5:map5topo_simple");
      childrenIds.add("lvl:luchtfoto-labels");
      root.setChildrenIds(childrenIds);
      app.getSettings()
          .putLayerSettingsItem("lyr:map5:map5topo", new AppLayerSettings().title("Map5"))
          .putLayerSettingsItem("lyr:map5:map5topo_simple", new AppLayerSettings().title("Map5 simple"));
      app.getContentRoot()
          .addBaseLayerNodesItem(new AppTreeLayerNode()
              .objectType("AppTreeLayerNode")
              .id("lyr:map5:map5topo")
              .serviceId("map5")
              .layerName("map5topo")
              .visible(false))
          .addBaseLayerNodesItem(new AppTreeLayerNode()
              .objectType("AppTreeLayerNode")
              .id("lyr:map5:map5topo_simple")
              .serviceId("map5")
              .layerName("map5topo_simple")
              .visible(false))
          .addBaseLayerNodesItem(new AppTreeLevelNode()
              .objectType("AppTreeLevelNode")
              .id("lvl:luchtfoto-labels")
              .title("Luchtfoto met labels")
              .addChildrenIdsItem("lyr:map5:luforoadslabels")
              .addChildrenIdsItem("lyr:pdok-hwh-luchtfotorgb:Actueel_orthoHR2"))
          .addBaseLayerNodesItem(new AppTreeLayerNode()
              .objectType("AppTreeLayerNode")
              .id("lyr:map5:luforoadslabels")
              .serviceId("map5")
              .layerName("luforoadslabels")
              .visible(false))
          .addBaseLayerNodesItem(new AppTreeLayerNode()
              .objectType("AppTreeLayerNode")
              .id("lyr:pdok-hwh-luchtfotorgb:Actueel_orthoHR2")
              .serviceId("pdok-hwh-luchtfotorgb")
              .layerName("Actueel_orthoHR")
              .visible(false));
    }

    applicationRepository.save(app);

    app = new Application()
        .setName("base")
        .setTitle("Service base app")
        .setCrs("EPSG:28992")
        .setAuthorizationRules(ruleAnonymousRead)
        .setContentRoot(new AppContent()
            .addBaseLayerNodesItem(new AppTreeLevelNode()
                .objectType("AppTreeLevelNode")
                .id("root-base-layers")
                .root(true)
                .title("Base layers")
                .childrenIds(List.of(
                    "lyr:openbasiskaart:osm", "lyr:pdok-hwh-luchtfotorgb:Actueel_orthoHR"))));
    app.getContentRoot().getBaseLayerNodes().addAll(baseNodes);
    applicationRepository.save(app);

    app = new Application()
        .setName("secured")
        .setTitle("secured app")
        .setCrs("EPSG:28992")
        .setAuthorizationRules(ruleLoggedIn)
        .setContentRoot(new AppContent()
            .addBaseLayerNodesItem(new AppTreeLevelNode()
                .objectType("AppTreeLevelNode")
                .id("root-base-layers")
                .root(true)
                .title("Base layers")
                .childrenIds(List.of(
                    "lyr:openbasiskaart:osm",
                    "lyr:pdok-hwh-luchtfotorgb:Actueel_orthoHR",
                    "lyr:openbasiskaart-proxied:osm")))
            .addBaseLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:openbasiskaart-proxied:osm")
                .serviceId("openbasiskaart-proxied")
                .layerName("osm")
                .visible(false))
            .addLayerNodesItem(new AppTreeLevelNode()
                .objectType("AppTreeLevelNode")
                .id("root")
                .root(true)
                .title("Layers")
                .childrenIds(List.of(
                    "lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied",
                    "lyr:pdok-kadaster-bestuurlijkegebieden:Gemeentegebied",
                    "lvl:proxied")))
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:pdok-kadaster-bestuurlijkegebieden:Gemeentegebied")
                .serviceId("pdok-kadaster-bestuurlijkegebieden")
                .layerName("Gemeentegebied")
                .visible(true))
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied")
                .serviceId("pdok-kadaster-bestuurlijkegebieden")
                .layerName("Provinciegebied")
                .visible(false))
            .addLayerNodesItem(new AppTreeLevelNode()
                .objectType("AppTreeLevelNode")
                .id("lvl:proxied")
                .title("Proxied")
                .childrenIds(List.of("lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel")))
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel")
                .serviceId("snapshot-geoserver-proxied")
                .layerName("postgis:begroeidterreindeel")
                .visible(false)))
        .setSettings(new AppSettings()
            .putLayerSettingsItem(
                "lyr:openbasiskaart-proxied:osm",
                new AppLayerSettings().title("Openbasiskaart (proxied)")));

    app.getContentRoot().getBaseLayerNodes().addAll(baseNodes);
    applicationRepository.save(app);

    app = new Application()
        .setName("secured-auth")
        .setTitle("secured (with authorizations)")
        .setCrs("EPSG:28992")
        .setAuthorizationRules(List.of(
            new AuthorizationRule()
                .groupName("test-foo")
                .decisions(Map.of(ACCESS_TYPE_VIEW, AuthorizationRuleDecision.ALLOW)),
            new AuthorizationRule()
                .groupName("test-bar")
                .decisions(Map.of(ACCESS_TYPE_VIEW, AuthorizationRuleDecision.ALLOW))))
        .setContentRoot(new AppContent()
            .addLayerNodesItem(new AppTreeLevelNode()
                .objectType("AppTreeLevelNode")
                .id("root")
                .root(true)
                .title("Layers")
                .childrenIds(List.of("lvl:needs-auth", "lvl:public")))
            .addLayerNodesItem(new AppTreeLevelNode()
                .objectType("AppTreeLevelNode")
                .id("lvl:public")
                .title("Public")
                .childrenIds(List.of("lyr:snapshot-geoserver:BGT")))
            .addLayerNodesItem(new AppTreeLevelNode()
                .objectType("AppTreeLevelNode")
                .id("lvl:needs-auth")
                .title("Needs auth")
                .childrenIds(List.of(
                    "lyr:filtered-snapshot-geoserver:BGT",
                    "lyr:filtered-snapshot-geoserver:postgis:begroeidterreindeel")))
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:filtered-snapshot-geoserver:BGT")
                .serviceId("filtered-snapshot-geoserver")
                .layerName("BGT")
                .visible(true))
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:filtered-snapshot-geoserver:postgis:begroeidterreindeel")
                .serviceId("filtered-snapshot-geoserver")
                .layerName("postgis:begroeidterreindeel")
                .visible(true))
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:snapshot-geoserver:BGT")
                .serviceId("snapshot-geoserver")
                .layerName("BGT")
                .visible(true)));

    applicationRepository.save(app);

    app = new Application()
        .setName("austria")
        .setCrs("EPSG:3857")
        .setAuthorizationRules(ruleAnonymousRead)
        .setTitle("Austria")
        .setInitialExtent(
            new Bounds().minx(987982d).miny(5799551d).maxx(1963423d).maxy(6320708d))
        .setMaxExtent(
            new Bounds().minx(206516d).miny(5095461d).maxx(3146930d).maxy(7096232d))
        .setContentRoot(new AppContent()
            .addBaseLayerNodesItem(new AppTreeLevelNode()
                .objectType("AppTreeLevelNode")
                .id("root-base-layers")
                .root(true)
                .title("Base layers")
                .childrenIds(List.of(
                    "lyr:at-basemap:geolandbasemap",
                    "lyr:at-basemap:orthofoto",
                    "lvl:orthofoto-labels",
                    "lyr:osm:xyz")))
            .addBaseLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:at-basemap:geolandbasemap")
                .serviceId("at-basemap")
                .layerName("geolandbasemap")
                .visible(true))
            .addBaseLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:at-basemap:orthofoto")
                .serviceId("at-basemap")
                .layerName("bmaporthofoto30cm")
                .visible(false))
            .addBaseLayerNodesItem(new AppTreeLevelNode()
                .objectType("AppTreeLevelNode")
                .id("lvl:orthofoto-labels")
                .title("Orthophoto with labels")
                .childrenIds(List.of("lyr:at-basemap:bmapoverlay", "lyr:at-basemap:orthofoto_2")))
            .addBaseLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:at-basemap:bmapoverlay")
                .serviceId("at-basemap")
                .layerName("bmapoverlay")
                .visible(false))
            .addBaseLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:at-basemap:orthofoto_2")
                .serviceId("at-basemap")
                .layerName("bmaporthofoto30cm")
                .visible(false))
            .addBaseLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:osm:xyz")
                .serviceId("osm")
                .layerName("xyz")
                .visible(false)));

    applicationRepository.save(app);

    app = new Application()
        .setName("3d_utrecht")
        .setCrs("EPSG:3857")
        .setAuthorizationRules(ruleLoggedIn)
        .setTitle("3D Utrecht")
        .setInitialExtent(
            new Bounds().minx(558390d).miny(6818485d).maxx(566751d).maxy(6824036d))
        .setMaxExtent(
            new Bounds().minx(91467d).miny(6496479d).maxx(1037043d).maxy(7147453d))
        .setSettings(new AppSettings().uiSettings(new AppUiSettings().enable3D(true)))
        .setContentRoot(new AppContent()
            .addBaseLayerNodesItem(new AppTreeLevelNode()
                .objectType("AppTreeLevelNode")
                .id("root-base-layers")
                .root(true)
                .title("Base layers")
                .childrenIds(List.of("lyr:pdok-hwh-luchtfotorgb:Actueel_orthoHR", "lyr:osm:xyz")))
            .addBaseLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:pdok-hwh-luchtfotorgb:Actueel_orthoHR")
                .serviceId("pdok-hwh-luchtfotorgb")
                .layerName("Actueel_orthoHR")
                .visible(true))
            .addBaseLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:osm:xyz")
                .serviceId("osm")
                .layerName("xyz")
                .visible(false))
            .addLayerNodesItem(new AppTreeLevelNode()
                .objectType("AppTreeLevelNode")
                .id("root")
                .root(true)
                .title("Layers")
                .childrenIds(List.of(
                    "lyr:3dbag_utrecht:tiles3d",
                    "lyr:snapshot-geoserver:postgis:begroeidterreindeel",
                    "lyr:3d_basisvoorziening_gebouwen_proxy:tiles3d",
                    "lyr:3d_utrecht_proxied_auth:tiles3d")))
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:3dbag_utrecht:tiles3d")
                .serviceId("3dbag_utrecht")
                .layerName("tiles3d")
                .visible(true))
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:3d_basisvoorziening_gebouwen_proxy:tiles3d")
                .serviceId("3d_basisvoorziening_gebouwen_proxy")
                .layerName("tiles3d")
                .visible(false))
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:3d_utrecht_proxied_auth:tiles3d")
                .serviceId("3d_utrecht_proxied_auth")
                .layerName("tiles3d")
                .visible(false))
            .addLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:snapshot-geoserver:postgis:begroeidterreindeel")
                .serviceId("snapshot-geoserver")
                .layerName("postgis:begroeidterreindeel")
                .visible(true))
            .addTerrainLayerNodesItem(new AppTreeLevelNode()
                .objectType("AppTreeLevelNode")
                .id("root-terrain-layers")
                .root(true)
                .title("Terrain Layers")
                .childrenIds(List.of("lyr:ahn_terrain_model:quantizedmesh")))
            .addTerrainLayerNodesItem(new AppTreeLayerNode()
                .objectType("AppTreeLayerNode")
                .id("lyr:ahn_terrain_model:quantizedmesh")
                .serviceId("ahn_terrain_model")
                .layerName("quantizedmesh")
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
  }

  private void createConfigurationTestData() throws JsonProcessingException {
    Configuration config = new Configuration();
    config.setKey("test");
    config.setAvailableForViewer(true);
    config.setValue("test value");
    config.setJsonValue(new ObjectMapper().readTree("{ \"someProperty\": 1, \"nestedObject\": { \"num\": 42 } }"));
    configurationRepository.save(config);
  }

  @Transactional
  public void createSolrIndex() throws Exception {
    if (connectToSpatialDbs) {
      // flush() the repo because we need to make sure feature type testdata is fully stored
      // before creating the Solr index (which requires access to the feature type settings)
      featureSourceRepository.flush();

      logger.info("Creating Solr index");
      @SuppressWarnings("PMD.AvoidUsingHardCodedIP")
      final String solrUrl = "http://" + (connectToSpatialDbsAtLocalhost ? "127.0.0.1" : "solr") + ":8983/solr/";
      this.solrService.setSolrUrl(solrUrl);
      SolrHelper solrHelper = new SolrHelper(this.solrService.getSolrClientForIndexing())
          .withBatchSize(solrBatchSize)
          .withGeometryValidationRule(solrGeometryValidationRule);
      GeoService geoService =
          geoServiceRepository.findById("snapshot-geoserver").orElseThrow();
      Application defaultApp = applicationRepository.findByName("default");

      TMFeatureType begroeidterreindeelFT = geoService.findFeatureTypeForLayer(
          geoService.findLayer("postgis:begroeidterreindeel"), featureSourceRepository);

      TMFeatureType wegdeelFT = geoService.findFeatureTypeForLayer(
          geoService.findLayer("sqlserver:wegdeel"), featureSourceRepository);

      TMFeatureType kadastraalPerceelFT = geoService.findFeatureTypeForLayer(
          geoService.findLayer("postgis:kadastraal_perceel"), featureSourceRepository);

      try (solrHelper) {
        SearchIndex begroeidterreindeelIndex = null;
        if (begroeidterreindeelFT != null) {
          begroeidterreindeelIndex = new SearchIndex()
              .setName("Begroeidterreindeel")
              .setFeatureTypeId(begroeidterreindeelFT.getId())
              .setSearchFieldsUsed(List.of("class", "plus_fysiekvoorkomen", "bronhouder"))
              .setSearchDisplayFieldsUsed(List.of("class", "plus_fysiekvoorkomen"));
          begroeidterreindeelIndex = searchIndexRepository.save(begroeidterreindeelIndex);
          begroeidterreindeelIndex = solrHelper.addFeatureTypeIndex(
              begroeidterreindeelIndex,
              begroeidterreindeelFT,
              featureSourceFactoryHelper,
              searchIndexRepository);
          begroeidterreindeelIndex = searchIndexRepository.save(begroeidterreindeelIndex);
        }

        SearchIndex kadastraalPerceelIndex = null;
        if (kadastraalPerceelFT != null) {
          kadastraalPerceelIndex = new SearchIndex()
              .setName("kadastraal_perceel")
              .setFeatureTypeId(kadastraalPerceelFT.getId())
              .setSearchFieldsUsed(List.of("aanduiding"))
              .setSearchDisplayFieldsUsed(List.of("aanduiding"));
          kadastraalPerceelIndex = searchIndexRepository.save(kadastraalPerceelIndex);
          kadastraalPerceelIndex = solrHelper.addFeatureTypeIndex(
              kadastraalPerceelIndex,
              kadastraalPerceelFT,
              featureSourceFactoryHelper,
              searchIndexRepository);
          kadastraalPerceelIndex = searchIndexRepository.save(kadastraalPerceelIndex);
        }

        SearchIndex wegdeelIndex = null;
        if (wegdeelFT != null) {
          wegdeelIndex = new SearchIndex()
              .setName("Wegdeel")
              .setFeatureTypeId(wegdeelFT.getId())
              .setSearchFieldsUsed(List.of(
                  "function_", "plus_fysiekvoorkomenwegdeel", "surfacematerial", "bronhouder"))
              .setSearchDisplayFieldsUsed(List.of("function_", "plus_fysiekvoorkomenwegdeel"));
          wegdeelIndex = searchIndexRepository.save(wegdeelIndex);
          wegdeelIndex = solrHelper.addFeatureTypeIndex(
              wegdeelIndex, wegdeelFT, featureSourceFactoryHelper, searchIndexRepository);
          wegdeelIndex = searchIndexRepository.save(wegdeelIndex);
        }

        featureSourceRepository
            .getByTitle("PostGIS")
            .flatMap(fs -> fs.getFeatureTypes().stream()
                .filter(ft -> ft.getName().equals("bak"))
                .findFirst())
            .ifPresent(ft -> {
              SearchIndex bak = new SearchIndex()
                  .setName("bak")
                  .setFeatureTypeId(ft.getId())
                  .setSearchFieldsUsed(List.of("gmlid", "identificatie", "plus_type"))
                  .setSearchDisplayFieldsUsed(List.of("gmlid", "plus_type", "bronhouder"));
              searchIndexRepository.save(bak);
              try {
                bak = solrHelper.addFeatureTypeIndex(
                    bak, ft, featureSourceFactoryHelper, searchIndexRepository);
                searchIndexRepository.save(bak);
              } catch (IOException | SolrServerException e) {
                throw new RuntimeException(e);
              }
            });

        // creating a solr index of config this will/should fail because there is no primary key in
        // the FT
        featureSourceRepository
            .getByTitle("PostGIS OSM")
            .flatMap(fs -> fs.getFeatureTypes().stream()
                .filter(ft -> ft.getName().equals("osm_roads"))
                .findFirst())
            .ifPresent(ft -> {
              SearchIndex osm_no_pk = new SearchIndex()
                  .setName("osm_no_pk")
                  .setFeatureTypeId(ft.getId())
                  .setSearchFieldsUsed(List.of("landuse", "osm_id", "natural", "boundary"))
                  .setSearchDisplayFieldsUsed(
                      List.of("landuse", "osm_id", "natural", "amenity", "boundary"));
              searchIndexRepository.save(osm_no_pk);
            });

        AppTreeLayerNode begroeidTerreindeelLayerNode = defaultApp
            .getAllAppTreeLayerNode()
            .filter(node -> node.getId().equals("lyr:snapshot-geoserver:postgis:begroeidterreindeel"))
            .findFirst()
            .orElse(null);

        if (begroeidTerreindeelLayerNode != null && begroeidterreindeelIndex != null) {
          defaultApp
              .getAppLayerSettings(begroeidTerreindeelLayerNode)
              .setSearchIndexId(begroeidterreindeelIndex.getId());
        }

        AppTreeLayerNode kadastraalPerceelLayerNode = defaultApp
            .getAllAppTreeLayerNode()
            .filter(node -> node.getId().equals("lyr:snapshot-geoserver:postgis:kadastraal_perceel"))
            .findFirst()
            .orElse(null);

        if (kadastraalPerceelLayerNode != null && kadastraalPerceelIndex != null) {
          defaultApp
              .getAppLayerSettings(kadastraalPerceelLayerNode)
              .setSearchIndexId(kadastraalPerceelIndex.getId());
        }

        AppTreeLayerNode wegdeel = defaultApp
            .getAllAppTreeLayerNode()
            .filter(node -> node.getId().equals("lyr:snapshot-geoserver:sqlserver:wegdeel"))
            .findFirst()
            .orElse(null);

        if (wegdeel != null && wegdeelIndex != null) {
          defaultApp.getAppLayerSettings(wegdeel).setSearchIndexId(wegdeelIndex.getId());
        }

        applicationRepository.save(defaultApp);
      }
    }
  }

  private void createScheduledTasks() {
    try {
      logger.info("Creating POC tasks");
      logger.info(
          "Created 15 minutely task with key: {}",
          taskManagerService.createTask(
              PocTask.class,
              new TMJobDataMap(Map.of(
                  Task.TYPE_KEY,
                  TaskType.POC.getValue(),
                  "foo",
                  "foobar",
                  Task.DESCRIPTION_KEY,
                  "POC task that runs every 15 minutes")),
              /* run every 15 minutes */ "0 0/15 * 1/1 * ? *"));
      logger.info(
          "Created hourly task with key: {}",
          taskManagerService.createTask(
              PocTask.class,
              new TMJobDataMap(Map.of(
                  Task.TYPE_KEY,
                  TaskType.POC.getValue(),
                  "foo",
                  "bar",
                  Task.DESCRIPTION_KEY,
                  "POC task that runs every hour",
                  Task.PRIORITY_KEY,
                  10)),
              /* run every hour */ "0 0 0/1 1/1 * ? *"));

      logger.info(
          "Created hourly failing task with key: {}",
          taskManagerService.createTask(
              FailingPocTask.class,
              new TMJobDataMap(Map.of(
                  Task.TYPE_KEY,
                  TaskType.FAILINGPOC.getValue(),
                  Task.DESCRIPTION_KEY,
                  "POC task that fails every hour with low priority",
                  Task.PRIORITY_KEY,
                  100)),
              /* run every hour */ "0 0 0/1 1/1 * ? *"));
      logger.info(
          "Created daily task with key: {}",
          taskManagerService.createTask(
              InterruptablePocTask.class,
              new TMJobDataMap(Map.of(
                  Task.TYPE_KEY,
                  TaskType.INTERRUPTABLEPOC.getValue(),
                  Task.DESCRIPTION_KEY,
                  "Interruptable POC task that runs every 15 minutes",
                  Task.PRIORITY_KEY,
                  5)),
              /* run every 15 minutes */ "0 0/15 * 1/1 * ? *"));
    } catch (SchedulerException e) {
      logger.error("Error creating scheduled one or more poc tasks", e);
    }

    if (categories.contains("search-index")) {
      logger.info("Creating INDEX tasks");
      List.of("Begroeidterreindeel", "kadastraal_perceel")
          .forEach(name -> searchIndexRepository.findByName(name).ifPresent(index -> {
            index.setSchedule(new TaskSchedule()
                /* hour */
                .cronExpression("0 0 0/1 1/1 * ? *")
                // /* 15 min */
                // .cronExpression("0 0/15 * 1/1 * ? *")
                .description("Update Solr index \" " + name + "\" every hour"));
            try {
              final UUID uuid = taskManagerService.createTask(
                  IndexTask.class,
                  new TMJobDataMap(Map.of(
                      Task.TYPE_KEY,
                      TaskType.INDEX,
                      Task.DESCRIPTION_KEY,
                      index.getSchedule().getDescription(),
                      IndexTask.INDEX_KEY,
                      index.getId().toString(),
                      Task.PRIORITY_KEY,
                      10)),
                  index.getSchedule().getCronExpression());

              index.getSchedule().setUuid(uuid);
              searchIndexRepository.save(index);

              logger.info("Created task to update Solr index with key: {}", uuid);
            } catch (SchedulerException e) {
              logger.error("Error creating scheduled solr index task", e);
            }
          }));
    }
  }

  private void createPages() throws IOException {
    Upload logo = new Upload()
        .setCategory(Upload.CATEGORY_PORTAL_IMAGE)
        .setFilename("gradient.svg")
        .setMimeType("image/svg+xml")
        .setContent(new ClassPathResource("test/gradient-logo.svg").getContentAsByteArray())
        .setLastModified(OffsetDateTime.now(ZoneId.systemDefault()));
    uploadRepository.save(logo);

    Page about = new Page();
    about.setName("about");
    about.setType("page");
    about.setContent("About Tailormap");
    about.setContent("""
# About Tailormap

This is a page about *Tailormap*. It doesn't say much yet.
""");
    pageRepository.save(about);

    Page page = new Page();
    page.setName("home");
    page.setType("page");
    page.setTitle("Tailormap - Home");
    page.setContent(
        """
# Welcome to Tailormap!

This page is only visible when you implement a frontend to display pages, or get it (including a simple CMS)
from [B3Partners](https://www.b3partners.nl)!
""");
    page.setClassName(null);
    page.setTiles(List.of(
        new PageTile()
            .id(UUID.randomUUID().toString())
            .title("Default app")
            .applicationId(Optional.ofNullable(applicationRepository.findByName("default"))
                .map(Application::getId)
                .orElse(null))
            .image(logo.getId().toString())
            .content("*Default app* tile content")
            .filterRequireAuthorization(false)
            .openInNewWindow(false),
        new PageTile()
            .id(UUID.randomUUID().toString())
            .title("Secured app")
            .applicationId(Optional.ofNullable(applicationRepository.findByName("secured"))
                .map(Application::getId)
                .orElse(null))
            .filterRequireAuthorization(true)
            .content("Secure app, only shown if user has authorization")
            .openInNewWindow(false),
        new PageTile()
            .id(UUID.randomUUID().toString())
            .title("Secured app (unfiltered)")
            .applicationId(Optional.ofNullable(applicationRepository.findByName("secured"))
                .map(Application::getId)
                .orElse(null))
            .filterRequireAuthorization(false)
            .content("Secure app, tile shown to everyone")
            .openInNewWindow(false),
        new PageTile()
            .id(UUID.randomUUID().toString())
            .title("About")
            .pageId(about.getId())
            .openInNewWindow(false),
        new PageTile()
            .id(UUID.randomUUID().toString())
            .title("B3Partners")
            .url("https://www.b3partners.nl/")
            .openInNewWindow(true)));
    pageRepository.save(page);

    Configuration c = new Configuration();
    c.setKey(HOME_PAGE);
    c.setValue(page.getId().toString());
    configurationRepository.save(c);

    List<MenuItem> globalMenuItems = List.of(
        new MenuItem().pageId(about.getId()).label("About").openInNewWindow(false),
        new MenuItem()
            .label("B3Partners website")
            .url("https://www.b3partners.nl/")
            .openInNewWindow(true)
            .exclusiveOnPageId(about.getId()));
    c = new Configuration();
    c.setKey(PORTAL_MENU);
    c.setJsonValue(new ObjectMapper().valueToTree(globalMenuItems));
    configurationRepository.save(c);
  }

  private void insertTestDrawing() {
    // note that the drawing uuid is hardcoded and used in the DrawingControllerIntegrationTest
    try {
      this.jdbcClient
          .sql(
              """
INSERT INTO data.drawing (id,name,description,domain_data,"access",created_by,created_at,updated_by,updated_at,srid,"version") VALUES
('38faa008-013e-49d4-9528-8f58c94d8791'::uuid,'Testcase','A private access drawing that is inserted as part of the testdata','{"items": 1, "domain": "test drawings"}','private','tm-admin','2025-02-27 17:53:36.095164+01','tm-admin','2025-02-27 17:54:19.384961+01',28992,1);
""")
          .update();

      this.jdbcClient
          .sql(
              """
INSERT INTO data.drawing_feature (drawing_id,id,geometry,properties) VALUES
('38faa008-013e-49d4-9528-8f58c94d8791'::uuid, '394e0d4a-b374-4d00-94c8-1758ea70e9d9'::uuid, 'SRID=28992;POLYGON ((131982.01 458929.27, 131957.31 458755.45, 132072.39 458736.69, 132099.06 458910.51, 131982.01 458929.27))'::public.geometry, '{"type": "POLYGON", "style": {"label": "", "marker": "circle", "fillColor": "rgb(117, 117, 117)", "labelSize": 15, "labelColor": "rgb(0, 0, 0)", "markerSize": 5, "fillOpacity": 30, "strokeColor": "rgb(98, 54, 255)", "strokeWidth": 3, "strokeOpacity": 100, "markerRotation": 0, "markerFillColor": "rgb(98, 54, 255)", "labelOutlineColor": "rgb(189, 189, 189)", "markerStrokeColor": "rgb(98, 54, 255)", "markerStrokeWidth": 1}}'::jsonb),
('38faa008-013e-49d4-9528-8f58c94d8791'::uuid, '89fc320c-41a3-4635-8d98-39d822339692'::uuid, 'SRID=28992;POINT (131897.55 458971.24)'::public.geometry, '{"type": "CIRCLE", "style": {"label": "CIRCLE", "marker": "circle", "fillColor": "rgb(117, 117, 117)", "labelSize": 15, "labelColor": "rgb(0, 0, 0)", "markerSize": 5, "fillOpacity": 30, "strokeColor": "rgb(98, 54, 255)", "strokeWidth": 3, "strokeOpacity": 100, "markerRotation": 0, "markerFillColor": "rgb(98, 54, 255)", "labelOutlineColor": "rgb(189, 189, 189)", "markerStrokeColor": "rgb(98, 54, 255)", "markerStrokeWidth": 1}, "radius": 14.989999999990687}'::jsonb),
('38faa008-013e-49d4-9528-8f58c94d8791'::uuid, '5d6252cc-25b6-4a43-9053-0779b86895a7'::uuid, 'SRID=28992;LINESTRING (131920.76 458754.96, 131923.73 458783.1, 131930.15 458793.97, 131945.95 458912.98, 131967.68 458946.06)'::public.geometry, '{"type": "LINE", "style": {"label": "", "marker": "circle", "fillColor": "rgb(255, 82, 82)", "labelSize": 15, "labelColor": "rgb(0, 0, 0)", "markerSize": 5, "fillOpacity": 30, "strokeColor": "rgb(255, 82, 82)", "strokeWidth": 13, "strokeOpacity": 100, "markerRotation": 0, "markerFillColor": "rgb(98, 54, 255)", "labelOutlineColor": "rgb(189, 189, 189)", "markerStrokeColor": "rgb(98, 54, 255)", "markerStrokeWidth": 1}}'::jsonb)
""")
          .update();
    } catch (Exception any) {
      logger.error("Error inserting test drawing in data schema, some tests may fail", any);
    }
  }

  private void insertDrawingStyle() throws IOException {
    Upload upload = new Upload()
        .setCategory(Upload.CATEGORY_DRAWING_STYLE_IMAGE)
        .setMimeType("image/svg+xml")
        .setFilename("ISO_7001_PI_PF_007.svg")
        .setContent(new ClassPathResource("test/ISO_7001_PI_PF_007.svg").getContentAsByteArray())
        .setLastModified(OffsetDateTime.now(ZoneId.systemDefault()));
    upload = uploadRepository.save(upload);
    UUID drinkwaterImageId = upload.getId();

    upload = new Upload()
        .setCategory(Upload.CATEGORY_DRAWING_STYLE_IMAGE)
        .setMimeType("image/svg+xml")
        .setFilename("lichtpunt.svg")
        .setContent(new ClassPathResource("test/lichtpunt.svg").getContentAsByteArray())
        .setLastModified(OffsetDateTime.now(ZoneId.systemDefault()));
    upload = uploadRepository.save(upload);
    UUID lichtpuntImageId = upload.getId();

    upload = new Upload()
        .setCategory(Upload.CATEGORY_DRAWING_STYLE_IMAGE)
        .setMimeType("image/svg+xml")
        .setFilename("ISO_7010_E003_-_First_aid_sign.svg")
        .setContent(new ClassPathResource("test/ISO_7010_E003_-_First_aid_sign.svg").getContentAsByteArray())
        .setLastModified(OffsetDateTime.now(ZoneId.systemDefault()));
    upload = uploadRepository.save(upload);
    UUID firstAidImageId = upload.getId();

    Properties props = new Properties();
    props.putAll(Map.of(
        "water-uuid", drinkwaterImageId.toString(),
        "lichtpunt-uuid", lichtpuntImageId.toString(),
        "first-aid-uuid", firstAidImageId.toString()));

    String drawingStyles =
        new ClassPathResource("test/object-drawing-style.json").getContentAsString(StandardCharsets.UTF_8);
    drawingStyles = new PropertyPlaceholderHelper("${", "}").replacePlaceholders(drawingStyles, props);

    // save the updated drawing style
    upload = new Upload()
        .setCategory(Upload.CATEGORY_DRAWING_STYLE)
        .setMimeType("application/json")
        .setFilename("object-drawing-style.json")
        .setContent(drawingStyles.getBytes(StandardCharsets.UTF_8))
        .setLastModified(OffsetDateTime.now(ZoneId.systemDefault()));
    uploadRepository.save(upload);
  }
}
