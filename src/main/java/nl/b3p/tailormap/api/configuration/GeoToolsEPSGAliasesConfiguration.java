/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.configuration;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import javax.annotation.PostConstruct;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.metadata.iso.citation.Citations;
import org.geotools.referencing.CRS;
import org.geotools.referencing.ReferencingFactoryFinder;
import org.geotools.referencing.factory.PropertyAuthorityFactory;
import org.geotools.referencing.factory.ReferencingFactoryContainer;
import org.geotools.referencing.wkt.Formattable;
import org.geotools.util.factory.Hints;
import org.opengis.referencing.FactoryException;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeoToolsEPSGAliasesConfiguration {
  private static final Log log = LogFactory.getLog(GeoToolsEPSGAliasesConfiguration.class);

  // Registering this unofficial EPSG code for web mercator is required to properly load a CRS like
  // urn:ogc:def:crs:EPSG:6.3:900913 used by MapCache, for example here:
  // https://www.openbasiskaart.nl/mapcache/wmts?REQUEST=GetCapabilities&SERVICE=WMTS
  private static final int[][] EPSG_ALIASES = {{900913, 3857}};

  @PostConstruct
  public void addEPSGAliases() throws IOException, FactoryException {
    // The PropertyAuthorityFactory only takes a URL parameter, create a temporary file
    File f = File.createTempFile("epsg", "properties");
    try (PrintWriter writer = new PrintWriter(f, StandardCharsets.US_ASCII)) {
      for (int[] alias : EPSG_ALIASES) {
        writer.printf("%d=%s\n", alias[0], ((Formattable) CRS.decode("EPSG:" + alias[1])).toWKT(0));
      }
    }

    Hints hints = new Hints(Hints.CRS_AUTHORITY_FACTORY, PropertyAuthorityFactory.class);
    ReferencingFactoryContainer referencingFactoryContainer =
        ReferencingFactoryContainer.instance(hints);

    PropertyAuthorityFactory factory =
        new PropertyAuthorityFactory(
            referencingFactoryContainer, Citations.fromName("EPSG"), f.toURI().toURL());

    ReferencingFactoryFinder.addAuthorityFactory(factory);
    ReferencingFactoryFinder.scanForPlugins();

    for (int[] alias : EPSG_ALIASES) {
      log.info(
          String.format("Added CRS alias to GeoTools: EPSG:%d -> EPSG:%d", alias[0], alias[1]));
    }
  }
}
