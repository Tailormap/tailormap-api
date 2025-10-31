/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.featuresources;

import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import org.apache.commons.dbcp.DelegatingConnection;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.jdbc.JDBCDataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.json.JDBCConnectionProperties;

/** Helper class for JDBC DataStores. */
public final class JDBCDataStoreHelper {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static String getPostGISCreateAttachmentsTableStatement(
      String tableName, String pkColumnName, String fkColumnType, String typeModifier, String schemaPrefix) {
    if (!schemaPrefix.isEmpty()) {
      schemaPrefix += ".";
    }
    return MessageFormat.format(
        """
CREATE TABLE IF NOT EXISTS {4}{0}_attachments (
{0}_pk          {2}{3}        NOT NULL REFERENCES {4}{0}({1}) ON DELETE CASCADE,
id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
file_name       VARCHAR(255),
attribute_name  VARCHAR(255) NOT NULL,
description     TEXT,
attachment      BYTEA        NOT NULL,
attachment_size INTEGER      NOT NULL,
mime_type       VARCHAR(100),
created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
created_by      VARCHAR(255) NOT NULL)
""",
        tableName, pkColumnName, fkColumnType, typeModifier, schemaPrefix);
  }

  private static String getSQLServerCreateAttachmentsTableStatement(
      String tableName, String pkColumnName, String fkColumnType, String typeModifier, String schemaPrefix) {
    // TODO SQL Server does not support IF NOT EXISTS for CREATE TABLE, we need to check for existence first
    // using IF OBJECT_ID('{0}_attachments', N'U') IS NULL
    if (!schemaPrefix.isEmpty()) {
      schemaPrefix += ".";
    }
    return MessageFormat.format(
        """
CREATE TABLE {4}{0}_attachments (
{0}_pk          {2}{3}         NOT NULL REFERENCES {4}{0}({1}) ON DELETE CASCADE,
attachment_id   UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
file_name       NVARCHAR(255),
attribute_name  VARCHAR(255)    NOT NULL,
description     NVARCHAR(MAX),
attachment      VARBINARY(MAX)   NOT NULL,
mime_type       NVARCHAR(100),
attachment_size INT              NOT NULL,
created_at      DATETIMEOFFSET   NOT NULL DEFAULT SYSDATETIMEOFFSET(),
created_by      NVARCHAR(255)    NOT NULL)
""",
        tableName, pkColumnName, fkColumnType, typeModifier, schemaPrefix);
  }

  private static String getOracleCreateAttachmentsTableStatement(
      String tableName, String pkColumnName, String fkColumnType, String typeModifier, String schemaPrefix) {
    if (!schemaPrefix.isEmpty()) {
      schemaPrefix += ".";
    }
    // Oracle supports  IF NOT EXISTS since 19.28
    return MessageFormat.format(
        """
CREATE TABLE IF NOT EXISTS {4}{0}_ATTACHMENTS (
{0}_PK          {2}{3}      NOT NULL REFERENCES {4}{0}({1}) ON DELETE CASCADE,
ATTACHMENT_ID   RAW(16)       DEFAULT SYS_GUID() PRIMARY KEY,
FILE_NAME       VARCHAR2(255),
ATTACHMENT      BLOB          NOT NULL,
ATTRIBUTE_NAME  VARCHAR2(255) NOT NULL,
DESCRIPTION     CLOB,
MIME_TYPE       VARCHAR2(100),
ATTACHMENT_SIZE INT           NOT NULL,
CREATED_AT      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
CREATED_BY      VARCHAR2(255) NOT NULL)
""",
        tableName, pkColumnName, fkColumnType, typeModifier, schemaPrefix);
  }

  /**
   * Get the SQL statement to create the attachments table for the given feature type.
   *
   * @param featureType The feature type
   * @return The SQL statement
   * @throws IOException If an error connecting to the database occurs
   * @throws IllegalArgumentException If the database type is not supported
   */
  public String getCreateAttachmentsForFeatureTypeStatements(@NotNull TMFeatureType featureType)
      throws IOException, IllegalArgumentException, SQLException {

    JDBCDataStore ds = null;
    String nativeType;
    String fkColumnType = null;
    int fkColumnSize = 0;
    try {
      ds = (JDBCDataStore) new JDBCFeatureSourceHelper().createDataStore(featureType.getFeatureSource());
      SimpleFeatureType simpleFeatureType = ds.getSchema(featureType.getName());
      AttributeDescriptor pkDescriptor = simpleFeatureType.getDescriptor(featureType.getPrimaryKeyAttribute());
      nativeType = (String) pkDescriptor.getUserData().get("org.geotools.jdbc.nativeTypeName");

      try (Connection conn = ((DelegatingConnection) ds.getDataSource().getConnection()).getInnermostDelegate()) {
        DatabaseMetaData metaData = conn.getMetaData();
        // Try with given case
        try (ResultSet rs = metaData.getColumns(
            conn.getCatalog(),
            ds.getDatabaseSchema(),
            featureType.getName(),
            featureType.getPrimaryKeyAttribute())) {
          if (rs.next()) {
            fkColumnType = rs.getString("TYPE_NAME");
            fkColumnSize = rs.getInt("COLUMN_SIZE");
          }
        }

        // Fallback to upper-case table/column names (common for some DBs, but something must be wrong in our
        // configuration because we store uppercase when we get that from the database...)
        if (fkColumnType == null) {
          try (ResultSet rs = metaData.getColumns(
              conn.getCatalog(),
              ds.getDatabaseSchema(),
              featureType.getName().toUpperCase(Locale.ROOT),
              featureType.getPrimaryKeyAttribute().toUpperCase(Locale.ROOT))) {
            if (rs.next()) {
              fkColumnType = rs.getString("TYPE_NAME");
              fkColumnSize = rs.getInt("COLUMN_SIZE");
            }
          }
        }

        // Final fallback to GeoTools nativeType from feature metadata and a default that might work
        if (fkColumnType == null) {
          fkColumnType = nativeType;
        }
      }

      logger.debug(
          "Creating attachment table for feature type with primary key {} (native type: {}, meta type: {}, size: {})",
          pkDescriptor.getLocalName(),
          nativeType,
          fkColumnType,
          fkColumnSize);
    } finally {
      if (ds != null) ds.dispose();
    }

    String typeModifier = "";
    if (fkColumnSize > 0) {
      typeModifier = getValidModifier(fkColumnType, fkColumnSize);
    }

    JDBCConnectionProperties connProperties = featureType.getFeatureSource().getJdbcConnection();
    switch (connProperties.getDbtype()) {
      case POSTGIS -> {
        return getPostGISCreateAttachmentsTableStatement(
            featureType.getName(),
            featureType.getPrimaryKeyAttribute(),
            fkColumnType,
            typeModifier,
            ds.getDatabaseSchema());
      }

      case ORACLE -> {
        return getOracleCreateAttachmentsTableStatement(
            featureType.getName(),
            featureType.getPrimaryKeyAttribute(),
            fkColumnType,
            typeModifier,
            ds.getDatabaseSchema());
      }
      case SQLSERVER -> {
        return getSQLServerCreateAttachmentsTableStatement(
            featureType.getName(),
            featureType.getPrimaryKeyAttribute(),
            fkColumnType,
            typeModifier,
            ds.getDatabaseSchema());
      }
      default ->
        throw new IllegalArgumentException(
            "Unsupported database type for attachments: " + connProperties.getDbtype());
    }
  }

  private static final List<String> allowedPKTypesSupportingSize = List.of(
      // list of database types that support size modifiers
      // for their foreign key columns
      // PostgreSQL types: https://www.postgresql.org/docs/current/datatype.html
      "CHARACTER",
      "CHARACTER VARYING",
      "CHAR",
      "VARCHAR",
      // numeric/decimal takes size and precision but we don't want to use floating point for FK columns...
      "NUMERIC",
      "DECIMAL",
      // SQL Server types:
      // https://learn.microsoft.com/en-us/sql/t-sql/data-types/data-types-transact-sql?view=sql-server-ver17
      "NVARCHAR",
      "NCHAR",
      // Oracle types
      "VARCHAR2",
      "NVARCHAR2",
      "NUMBER",
      "RAW");

  private String getValidModifier(String columnType, int fkColumnSize) {
    if (fkColumnSize > 0 && allowedPKTypesSupportingSize.contains(columnType.toUpperCase(Locale.ROOT))) {
      if (columnType.equalsIgnoreCase("NUMERIC")
          || columnType.equalsIgnoreCase("DECIMAL")
          || columnType.equalsIgnoreCase("NUMBER")) {
        // For NUMERIC/DECIMAL we should ideally also get the precision, but for FK columns
        // we just use size with default precision 0
        return "(" + fkColumnSize + ",0)";
      }
      return "(" + fkColumnSize + ")";
    } else {
      return "";
    }
  }

  /**
   * Get the SQL statement to create the attachments foreign key index for the given feature type.
   *
   * @param featureType The feature type
   * @return The SQL statement
   * @throws IllegalArgumentException If the database type is not supported
   */
  public String getCreateAttachmentsIndexForFeatureTypeStatements(@NotNull TMFeatureType featureType)
      throws IllegalArgumentException {

    String schemaPrefix = "";
    JDBCDataStore ds = null;
    try {
      ds = (JDBCDataStore) new JDBCFeatureSourceHelper().createDataStore(featureType.getFeatureSource());
      schemaPrefix = ds.getDatabaseSchema();
      if (!schemaPrefix.isEmpty()) {
        schemaPrefix += ".";
      }
    } catch (IOException e) {
      logger.error("Failed to open GeoTools datastore, could not retrieve active schema", e);
    } finally {
      if (ds != null) ds.dispose();
    }

    JDBCConnectionProperties connProperties = featureType.getFeatureSource().getJdbcConnection();
    switch (connProperties.getDbtype()) {
      case POSTGIS -> {
        return MessageFormat.format(
            "CREATE INDEX IF NOT EXISTS {0}_attachments_fk ON {1}{0}_attachments({0}_pk)",
            featureType.getName(), schemaPrefix);
      }
      case SQLSERVER -> {
        return MessageFormat.format(
            // TODO SQL Server does not support IF NOT EXISTS for CREATE INDEX
            "CREATE INDEX {0}_attachments_fk ON {1}{0}_attachments({0}_pk)",
            featureType.getName(), schemaPrefix);
      }
      case ORACLE -> {
        return MessageFormat.format(
                "CREATE INDEX IF NOT EXISTS {1}{0}_attachments_fk ON {1}{0}_attachments({0}_pk)",
                featureType.getName(), schemaPrefix)
            .toUpperCase(Locale.ROOT);
      }
      default ->
        throw new IllegalArgumentException(
            "Unsupported database type for attachments: " + connProperties.getDbtype());
    }
  }
}
