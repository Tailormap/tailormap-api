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
import org.geotools.data.ResourceInfo;
import org.geotools.data.simple.SimpleFeatureSource;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.AttributeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class FeatureSourceHelper {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public DataStore createDataStore(TMFeatureSource tmfs) throws IOException {
    return createDataStore(tmfs, null);
  }

  public abstract DataStore createDataStore(TMFeatureSource tmfs, Integer timeout)
      throws IOException;

  public SimpleFeatureSource openGeoToolsFeatureSource(
      TMFeatureSource tmfs, TMFeatureType sft, Integer timeout) throws IOException {
    DataStore ds = createDataStore(tmfs, timeout);
    return ds.getFeatureSource(sft.getName());
  }

  public void loadCapabilities(TMFeatureSource tmfs) throws IOException {
    loadCapabilities(tmfs, null);
  }

  public DataStore openDatastore(Map<String, Object> params, String passwordKey)
      throws IOException {
    Map<String, Object> logParams = new HashMap<>(params);
    String passwd = (String) params.get(passwordKey);
    if (passwd != null) {
      logParams.put(passwordKey, String.valueOf(new char[passwd.length()]).replace("\0", "*"));
    }
    logger.debug("Opening datastore using parameters: {}", logParams);
    DataStore ds;
    try {
      ds = DataStoreFinder.getDataStore(params);
    } catch (Exception e) {
      throw new IOException("Cannot open datastore using parameters: " + logParams, e);
    }
    if (ds == null) {
      throw new IOException("No datastore found using parameters " + logParams);
    }
    return ds;
  }

  public void loadCapabilities(TMFeatureSource tmfs, Integer timeout) throws IOException {
    DataStore ds = createDataStore(tmfs, timeout);
    try {
      if (ds.getInfo().getTitle() != null) {
        tmfs.setTitle(ds.getInfo().getTitle());
      }

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
      logger.info("Type names for JDBC {}: {}", tmfs.getUrl(), Arrays.toString(typeNames));

      for (String typeName : typeNames) {
        TMFeatureType pft =
            new TMFeatureType()
                .setName(typeName)
                .setFeatureSource(tmfs)
                .setWriteable(true); // TODO set writeable meaningfully
        tmfs.getFeatureTypes().add(pft);
        try {
          logger.debug("Get feature source from GeoTools datastore for type \"{}\"", typeName);
          SimpleFeatureSource gtFs = ds.getFeatureSource(typeName);
          ResourceInfo info = gtFs.getInfo();
          if (info != null) {
            pft.setTitle(info.getTitle());
            pft.setInfo(getFeatureTypeInfo(pft, info, gtFs));

            SimpleFeatureType gtFt = gtFs.getSchema();
            for (AttributeDescriptor gtAttr : gtFt.getAttributeDescriptors()) {
              AttributeType type = gtAttr.getType();
              TMAttributeDescriptor tmAttr =
                  new TMAttributeDescriptor()
                      .setName(gtAttr.getLocalName())
                      .setType(GeoToolsHelper.toAttributeType(type))
                      .setDescription(
                          type.getDescription() == null ? null : type.getDescription().toString());
              if (tmAttr.getType() == TMAttributeType.OBJECT) {
                tmAttr.setUnknownTypeClassName(type.getBinding().getName());
              }
              pft.getAttributes().add(tmAttr);
            }
          }
        } catch (Exception e) {
          logger.error("Exception reading feature type \"{}\"", typeName, e);
        }
      }
    } finally {
      ds.dispose();
    }
  }

  protected TMFeatureTypeInfo getFeatureTypeInfo(
      TMFeatureType pft, ResourceInfo info, SimpleFeatureSource gtFs) {
    return new TMFeatureTypeInfo()
        .keywords(info.getKeywords())
        .description(info.getDescription())
        .bounds(GeoToolsHelper.fromEnvelope(info.getBounds()))
        .crs(crsToString(info.getCRS()));
  }
}
