/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.featuresources;

import static org.geotools.jdbc.JDBCDataStore.JDBC_PRIMARY_KEY_COLUMN;
import static org.geotools.jdbc.JDBCDataStore.JDBC_READ_ONLY;
import static org.tailormap.api.persistence.helper.GeoToolsHelper.crsToString;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.geotools.api.data.DataStore;
import org.geotools.api.data.DataStoreFinder;
import org.geotools.api.data.ResourceInfo;
import org.geotools.api.data.SimpleFeatureSource;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.api.feature.type.AttributeType;
import org.geotools.jdbc.JDBCFeatureStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tailormap.api.persistence.TMFeatureSource;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.helper.GeoToolsHelper;
import org.tailormap.api.persistence.json.TMAttributeDescriptor;
import org.tailormap.api.persistence.json.TMAttributeType;
import org.tailormap.api.persistence.json.TMFeatureTypeInfo;
import org.tailormap.api.persistence.json.TMServiceCaps;
import org.tailormap.api.persistence.json.TMServiceInfo;

public abstract class FeatureSourceHelper {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public DataStore createDataStore(TMFeatureSource tmfs) throws IOException {
    return createDataStore(tmfs, null);
  }

  public abstract DataStore createDataStore(TMFeatureSource tmfs, Integer timeout) throws IOException;

  public SimpleFeatureSource openGeoToolsFeatureSource(TMFeatureType tmft, Integer timeout) throws IOException {
    DataStore ds = createDataStore(tmft.getFeatureSource(), timeout);
    return ds.getFeatureSource(tmft.getName());
  }

  public void loadCapabilities(TMFeatureSource tmfs) throws IOException {
    loadCapabilities(tmfs, null);
  }

  public DataStore openDatastore(Map<String, Object> params, String passwordKey) throws IOException {
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
      if (StringUtils.isBlank(tmfs.getTitle())) {
        tmfs.setTitle(ds.getInfo().getTitle());
      }

      org.geotools.api.data.ServiceInfo si = ds.getInfo();
      tmfs.setServiceCapabilities(new TMServiceCaps()
          .serviceInfo(new TMServiceInfo()
              .title(si.getTitle())
              .keywords(si.getKeywords())
              .description(si.getDescription())
              .publisher(si.getPublisher())
              .schema(si.getSchema())
              .source(si.getSource())));

      List<String> typeNames = Arrays.asList(ds.getTypeNames());
      logger.info(
          "Type names for {} {}: {}",
          tmfs.getProtocol().getValue(),
          tmfs.getProtocol() == TMFeatureSource.Protocol.WFS ? tmfs.getUrl() : tmfs.getJdbcConnection(),
          typeNames);

      tmfs.getFeatureTypes().removeIf(tmft -> {
        if (!typeNames.contains(tmft.getName())) {
          logger.info("Feature type removed: {}", tmft.getName());
          return true;
        } else {
          return false;
        }
      });

      for (String typeName : typeNames) {
        TMFeatureType pft = tmfs.getFeatureTypes().stream()
            .filter(ft -> ft.getName().equals(typeName))
            .findFirst()
            .orElseGet(() -> new TMFeatureType().setName(typeName).setFeatureSource(tmfs));
        if (!tmfs.getFeatureTypes().contains(pft)) {
          tmfs.getFeatureTypes().add(pft);
        }
        try {
          logger.debug("Get feature source from GeoTools datastore for type \"{}\"", typeName);
          SimpleFeatureSource gtFs = ds.getFeatureSource(typeName);
          ResourceInfo info = gtFs.getInfo();
          if (info != null) {
            pft.setTitle(info.getTitle());
            pft.setInfo(getFeatureTypeInfo(pft, info, gtFs));
            pft.getAttributes().clear();

            SimpleFeatureType gtFt = gtFs.getSchema();
            pft.setWriteable(gtFs instanceof JDBCFeatureStore
                && !Boolean.TRUE.equals(gtFt.getUserData().get(JDBC_READ_ONLY)));
            String primaryKeyName = null;
            for (AttributeDescriptor gtAttr : gtFt.getAttributeDescriptors()) {
              AttributeType type = gtAttr.getType();
              if (Boolean.TRUE.equals(gtAttr.getUserData().get(JDBC_PRIMARY_KEY_COLUMN))) {
                if (primaryKeyName == null) {
                  logger.debug(
                      "Found primary key attribute \"{}\" for type \"{}\"",
                      gtAttr.getLocalName(),
                      typeName);
                  primaryKeyName = gtAttr.getLocalName();
                } else {
                  logger.warn(
                      "Multiple primary key attributes found for type \"{}\": \"{}\" and \"{}\". Composite primary keys are not supported for writing at the moment, setting as read-only.",
                      typeName,
                      primaryKeyName,
                      gtAttr.getLocalName());
                  pft.setWriteable(false);
                }
              }
              TMAttributeDescriptor tmAttr = new TMAttributeDescriptor()
                  .name(gtAttr.getLocalName())
                  .type(GeoToolsHelper.toAttributeType(type))
                  .nullable(gtAttr.isNillable())
                  .defaultValue(
                      gtAttr.getDefaultValue() == null
                          ? null
                          : gtAttr.getDefaultValue().toString())
                  .description(
                      type.getDescription() == null
                          ? null
                          : type.getDescription().toString());
              if (tmAttr.getType() == TMAttributeType.OBJECT) {
                tmAttr.setUnknownTypeClassName(type.getBinding().getName());
              }
              pft.getAttributes().add(tmAttr);
            }
            pft.setPrimaryKeyAttribute(primaryKeyName);
            pft.setDefaultGeometryAttribute(pft.findDefaultGeometryAttribute());
          }
        } catch (Exception e) {
          logger.error("Exception reading feature type \"{}\"", typeName, e);
        }
      }
    } finally {
      ds.dispose();
    }
  }

  protected TMFeatureTypeInfo getFeatureTypeInfo(TMFeatureType pft, ResourceInfo info, SimpleFeatureSource gtFs) {
    return new TMFeatureTypeInfo()
        .keywords(info.getKeywords())
        .description(info.getDescription())
        .bounds(GeoToolsHelper.fromEnvelope(info.getBounds()))
        .crs(crsToString(info.getCRS()));
  }
}
