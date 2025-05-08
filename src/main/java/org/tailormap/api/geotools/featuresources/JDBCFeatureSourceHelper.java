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
import static org.tailormap.api.persistence.json.JDBCConnectionProperties.DbtypeEnum.ORACLE;
import static org.tailormap.api.persistence.json.JDBCConnectionProperties.DbtypeEnum.POSTGIS;
import static org.tailormap.api.persistence.json.JDBCConnectionProperties.DbtypeEnum.SQLSERVER;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.geotools.api.data.DataStore;
import org.geotools.data.postgis.PostgisNGDataStoreFactory;
import org.geotools.data.sqlserver.SQLServerDataStoreFactory;
import org.tailormap.api.persistence.TMFeatureSource;
import org.tailormap.api.persistence.json.JDBCConnectionProperties;
import org.tailormap.api.persistence.json.ServiceAuthentication;

public class JDBCFeatureSourceHelper extends FeatureSourceHelper {
  private static final Map<JDBCConnectionProperties.DbtypeEnum, Integer> defaultPorts =
      Map.of(POSTGIS, 5432, ORACLE, 1521, SQLSERVER, 1433);

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

    Map<String, Object> params = new HashMap<>();
    // database specific settings
    switch (c.getDbtype()) {
      case POSTGIS -> {
        // use spatial index to estimate the extents
        params.put(PostgisNGDataStoreFactory.ESTIMATED_EXTENTS.key, true);
        if (!connectionOpts.contains("ApplicationName")) {
          connectionOpts = connectionOpts
              + (connectionOpts.contains("?") ? "&amp;" : "?")
              + "ApplicationName=tailormap-api";
        }
      }
      case SQLSERVER -> {
        // use spatial index to estimate the extents
        params.put(SQLServerDataStoreFactory.ESTIMATED_EXTENTS.key, true);
        // we need this for mssql to determine a feature type on an empty table
        params.put(GEOMETRY_METADATA_TABLE.key, "geometry_columns");
        if (!connectionOpts.contains("applicationName")) {
          // see
          // https://learn.microsoft.com/en-us/sql/connect/jdbc/building-the-connection-url?view=sql-server-ver16
          connectionOpts = connectionOpts + ";applicationName=tailormap-api";
        }
      }
        // No specific settings for Oracle
      case ORACLE -> {}
    }

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

    return openDatastore(params, PASSWD.key);
  }
}
