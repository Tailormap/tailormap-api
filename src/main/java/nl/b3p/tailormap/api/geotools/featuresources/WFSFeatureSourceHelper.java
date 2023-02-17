/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.geotools.featuresources;

import static nl.b3p.tailormap.api.persistence.helper.GeoToolsHelper.crsToString;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import nl.b3p.tailormap.api.persistence.TMAttributeDescriptor;
import nl.b3p.tailormap.api.persistence.TMFeatureSource;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.persistence.helper.GeoToolsHelper;
import nl.b3p.tailormap.api.persistence.json.TMAttributeType;
import nl.b3p.tailormap.api.persistence.json.TMFeatureTypeInfo;
import nl.b3p.tailormap.api.persistence.json.TMServiceCaps;
import nl.b3p.tailormap.api.persistence.json.TMServiceInfo;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.wfs.WFSDataStoreFactory;
import org.geotools.data.wfs.internal.FeatureTypeInfo;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.AttributeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WFSFeatureSourceHelper implements FeatureSourceHelper {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static DataStore createDataStore(
      Map<String, Object> extraDataStoreParams, TMFeatureSource fs) throws IOException {
    Map<String, Object> params = new HashMap<>();

    if (extraDataStoreParams != null) {
      params.putAll(extraDataStoreParams);
    }

    // Params which can not be overridden below
    String wfsUrl = fs.getUrl();
    if (!wfsUrl.endsWith("&") && !wfsUrl.endsWith("?")) {
      wfsUrl += wfsUrl.contains("?") ? "&" : "?";
    }
    wfsUrl = wfsUrl + "REQUEST=GetCapabilities&SERVICE=WFS";
    if (!wfsUrl.toUpperCase().contains("VERSION")) {
      wfsUrl += "&VERSION=1.1.0";
    }

    params.put(WFSDataStoreFactory.URL.key, wfsUrl);
    // params.put(WFSDataStoreFactory.USERNAME.key, fs.getUsername());
    // params.put(WFSDataStoreFactory.PASSWORD.key, fs.getPassword());

    Map<String, Object> logParams = new HashMap<>(params);
    //    if (fs.getPassword() != null) {
    //      logParams.put(
    //          WFSDataStoreFactory.PASSWORD.key,
    //          String.valueOf(new char[fs.getPassword().length()]).replace("\0", "*"));
    //    }
    logger.debug("Opening datastore using parameters: {}", logParams);
    DataStore ds = DataStoreFinder.getDataStore(params);
    if (ds == null) {
      throw new IOException("Cannot open datastore using parameters " + logParams);
    }
    return ds;
  }

  @Override
  public SimpleFeatureSource openGeoToolsFeatureSource(
      TMFeatureSource fs, TMFeatureType sft, int timeout) throws IOException {
    Map<String, Object> extraParams = new HashMap<>();
    extraParams.put(WFSDataStoreFactory.TIMEOUT.key, timeout);
    DataStore ds = WFSFeatureSourceHelper.createDataStore(extraParams, fs);

    return ds.getFeatureSource(sft.getName());
  }

  public void loadCapabilities(TMFeatureSource tmfs, int timeout) throws IOException {
    Map<String, Object> extraParams = new HashMap<>();
    extraParams.put(WFSDataStoreFactory.TIMEOUT.key, timeout);
    logger.debug("Create datastore for WFS {}", tmfs.getUrl());
    DataStore ds = WFSFeatureSourceHelper.createDataStore(extraParams, tmfs);

    tmfs.setTitle(ds.getInfo().getTitle());

    org.geotools.data.ServiceInfo si = ds.getInfo();
    tmfs.setServiceCapabilities(
        new TMServiceCaps()
            .serviceInfo(
                new TMServiceInfo()
                    .title(si.getTitle())
                    .keywords(si.getKeywords())
                    .description(si.getDescription())
                    .publisher(si.getPublisher())
                    .schema(si.getSchema())
                    .source(si.getSource())));

    String[] typeNames = ds.getTypeNames();
    logger.info("Type names for WFS {}: {}", tmfs.getUrl(), Arrays.toString(typeNames));

    for (String typeName : typeNames) {
      TMFeatureType pft =
          new TMFeatureType().setName(typeName).setFeatureSource(tmfs).setWriteable(false);
      tmfs.getFeatureTypes().add(pft);
      try {
        logger.debug("Get feature source from GeoTools datastore for typeName {}", typeName);
        SimpleFeatureSource gtFs = ds.getFeatureSource(typeName);
        FeatureTypeInfo info = (FeatureTypeInfo) gtFs.getInfo();
        if (gtFs.getInfo() != null) {
          pft.setTitle(info.getTitle());
          pft.setInfo(
              new TMFeatureTypeInfo()
                  .keywords(info.getKeywords())
                  .description(info.getDescription())
                  .schema(info.getSchema())
                  .bounds(GeoToolsHelper.fromEnvelope(info.getBounds()))
                  .crs(crsToString(info.getCRS()))
                  .wgs84BoundingBox(GeoToolsHelper.fromEnvelope(info.getWGS84BoundingBox()))
                  .defaultSrs(info.getDefaultSRS())
                  .otherSrs(Set.copyOf(info.getOtherSRS()))
                  .outputFormats(info.getOutputFormats())
                  .abstractText(info.getAbstract()));

          SimpleFeatureType gtFt = gtFs.getSchema();
          for (AttributeDescriptor gtAttr : gtFt.getAttributeDescriptors()) {
            AttributeType type = gtAttr.getType();
            TMAttributeDescriptor tmAttr =
                new TMAttributeDescriptor()
                    .setName(gtAttr.getLocalName())
                    .setType(GeoToolsHelper.toAttributeType(type))
                    .setIdentified(type.isIdentified())
                    .setDescription(
                        type.getDescription() == null ? null : type.getDescription().toString());
            if (tmAttr.getType() == TMAttributeType.OBJECT) {
              tmAttr.setUnknownTypeClassName(type.getClass().getName());
            }
            pft.getAttributes().add(tmAttr);
          }
        }
      } catch (Exception e) {
        logger.error(
            "Exception reading feature type \"{}\": {}: {}",
            typeName,
            e.getClass(),
            e.getMessage());
        logger.trace("Stacktrace", e);
      }
    }
  }
}
