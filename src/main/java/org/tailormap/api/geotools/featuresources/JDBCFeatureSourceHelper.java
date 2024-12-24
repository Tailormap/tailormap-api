/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.featuresources;

import static org.geotools.data.sqlserver.SQLServerDataStoreFactory.GEOMETRY_METADATA_TABLE;
import static org.geotools.jdbc.JDBCDataStoreFactory.DATABASE;
import static org.geotools.jdbc.JDBCDataStoreFactory.DBTYPE;
import static org.geotools.jdbc.JDBCDataStoreFactory.EXPOSE_PK;
import static org.geotools.jdbc.JDBCDataStoreFactory.FETCHSIZE;
import static org.geotools.jdbc.JDBCDataStoreFactory.HOST;
import static org.geotools.jdbc.JDBCDataStoreFactory.MAXWAIT;
import static org.geotools.jdbc.JDBCDataStoreFactory.PASSWD;
import static org.geotools.jdbc.JDBCDataStoreFactory.PK_METADATA_TABLE;
import static org.geotools.jdbc.JDBCDataStoreFactory.PORT;
import static org.geotools.jdbc.JDBCDataStoreFactory.SCHEMA;
import static org.geotools.jdbc.JDBCDataStoreFactory.USER;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.geotools.api.data.DataStore;
import org.tailormap.api.persistence.TMFeatureSource;
import org.tailormap.api.persistence.json.JDBCConnectionProperties;
import org.tailormap.api.persistence.json.ServiceAuthentication;

public class JDBCFeatureSourceHelper extends FeatureSourceHelper {
  private static final Map<JDBCConnectionProperties.DbtypeEnum, Integer> defaultPorts = Map.of(
      JDBCConnectionProperties.DbtypeEnum.POSTGIS, 5432,
      JDBCConnectionProperties.DbtypeEnum.ORACLE, 1521,
      JDBCConnectionProperties.DbtypeEnum.SQLSERVER, 1433);

  @Override
  public DataStore createDataStore(TMFeatureSource tmfs, Integer timeout) throws IOException {
    if (tmfs.getProtocol() != TMFeatureSource.Protocol.JDBC) {
      throw new IllegalArgumentException(tmfs.getProtocol().getValue());
    }
    Objects.requireNonNull(tmfs.getJdbcConnection());
    Objects.requireNonNull(tmfs.getAuthentication());
    if (tmfs.getAuthentication().getMethod() != ServiceAuthentication.MethodEnum.PASSWORD) {
      throw new IllegalArgumentException(
          tmfs.getAuthentication().getMethod().getValue());
    }

    JDBCConnectionProperties c = tmfs.getJdbcConnection();
    Objects.requireNonNull(c.getDbtype());
    String connectionOpts = Optional.ofNullable(c.getAdditionalProperties().get("connectionOptions"))
        .orElse("");
    if (c.getDbtype() == JDBCConnectionProperties.DbtypeEnum.POSTGIS
        && !connectionOpts.contains("ApplicationName")) {
      connectionOpts =
          connectionOpts + (connectionOpts.contains("?") ? "&amp;" : "?") + "ApplicationName=tailormap-api";
    }

    Map<String, Object> params = new HashMap<>();
    params.put(DBTYPE.key, c.getDbtype().getValue());
    params.put(HOST.key, c.getHost());
    params.put(PORT.key, c.getPort() != null ? c.getPort() : defaultPorts.get(c.getDbtype()));
    params.put(DATABASE.key, c.getDatabase() + connectionOpts);
    params.put(SCHEMA.key, c.getSchema());
    params.put(USER.key, tmfs.getAuthentication().getUsername());
    params.put(PASSWD.key, tmfs.getAuthentication().getPassword());
    params.put(FETCHSIZE.key, c.getFetchSize());
    params.put(EXPOSE_PK.key, true);
    params.put(PK_METADATA_TABLE.key, c.getPrimaryKeyMetadataTable());
    params.put(MAXWAIT.key, timeout);
    if (c.getDbtype() != JDBCConnectionProperties.DbtypeEnum.ORACLE) {
      // this key is available in ao. Oracle and MS SQL datastore factories, but not in the common
      // parent...
      // we need this for mssql to determine a feature type on an empty table
      params.put(GEOMETRY_METADATA_TABLE.key, "geometry_columns");
    }
    return openDatastore(params, PASSWD.key);
  }
}
