/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.featuresources;

import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.text.MessageFormat;
import java.util.Locale;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.json.JDBCConnectionProperties;

/** Helper class for JDBC DataStores. */
public final class JDBCDataStoreHelper {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static String getPostGISCreateAttachmentsTableStatement(
      String tableName, String pkColumnName, String pkColumnType) {
    return MessageFormat.format(
        """
CREATE TABLE {0}_attachments (
{0}_pk          {2}          NOT NULL,
id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
file_name       VARCHAR(255),
attribute_name  VARCHAR(255) NOT NULL,
description     TEXT,
attachment      BYTEA        NOT NULL,
attachment_size INTEGER      NOT NULL,
mime_type       VARCHAR(100),
created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
created_by      VARCHAR(255) NOT NULL,
CONSTRAINT fk_{0}_attachments_{0} FOREIGN KEY ({0}_pk) REFERENCES {0} ({1}) ON DELETE CASCADE
);
""",
        tableName, pkColumnName, pkColumnType);
  }

  private static String getSQLServerCreateAttachmentsTableStatement(
      String tableName, String pkColumnName, String pkColumnType) {
    return MessageFormat.format(
        """
CREATE TABLE {0}_attachments (
{0}_pk          {2}              NOT NULL CONSTRAINT fk_attachments_{0} REFERENCES {0} ({1}) ON DELETE CASCADE,
attachment_id   UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
file_name       NVARCHAR(255),
attribute_name  VARCHAR(255)    NOT NULL,
description     NVARCHAR(MAX),
attachment      VARBINARY(MAX)   NOT NULL,
mime_type       NVARCHAR(100),
attachment_size INT              NOT NULL,
created_at      DATETIMEOFFSET   NOT NULL DEFAULT SYSDATETIMEOFFSET(),
created_by      NVARCHAR(255)    NOT NULL,

);
""",
        tableName, pkColumnName, pkColumnType);
  }

  private static String getOracleCreateAttachmentsTableStatement(
      String tableName, String pkColumnName, String pkColumnType) {
    return MessageFormat.format(
        """
CREATE TABLE {0}_ATTACHMENTS (
{0}_PK         {2}      NOT NULL REFERENCES {0} ({1}) ON DELETE CASCADE,
ATTACHMENT_ID   RAW(16)                  DEFAULT SYS_GUID() PRIMARY KEY,
FILE_NAME       VARCHAR2(255),
ATTACHMENT      BLOB          NOT NULL,
ATTRIBUTE_NAME  VARCHAR2(255) NOT NULL,
DESCRIPTION     CLOB,
MIME_TYPE       VARCHAR2(100),
ATTACHMENT_SIZE INT           NOT NULL,
CREATED_AT      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
CREATED_BY      VARCHAR2(255) NOT NULL
);
""",
        tableName, pkColumnName, pkColumnType);
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
      throws IOException, IllegalArgumentException {
    JDBCConnectionProperties connProperties = featureType.getFeatureSource().getJdbcConnection();
    SimpleFeatureType simpleFeatureType = new JDBCFeatureSourceHelper()
        .createDataStore(featureType.getFeatureSource())
        .getSchema(featureType.getName());
    AttributeDescriptor pkDescriptor = simpleFeatureType.getDescriptor(featureType.getPrimaryKeyAttribute());
    String nativeType = (String) pkDescriptor.getUserData().get("org.geotools.jdbc.nativeTypeName");

    logger.debug(
        "Creating attachment table for feature type with primary key {} (\nnative type: {})",
        pkDescriptor,
        nativeType);

    switch (connProperties.getDbtype()) {
      case POSTGIS -> {
        return getPostGISCreateAttachmentsTableStatement(
            featureType.getName(), featureType.getPrimaryKeyAttribute(), nativeType);
      }

      case ORACLE -> {
        return getOracleCreateAttachmentsTableStatement(
            featureType.getName(), featureType.getPrimaryKeyAttribute(), nativeType);
      }
      case SQLSERVER -> {
        return getSQLServerCreateAttachmentsTableStatement(
            featureType.getName(), featureType.getPrimaryKeyAttribute(), nativeType);
      }
      default ->
        throw new IllegalArgumentException(
            "Unsupported database type for attachments: " + connProperties.getDbtype());
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
    JDBCConnectionProperties connProperties = featureType.getFeatureSource().getJdbcConnection();
    String sql = MessageFormat.format(
        "CREATE INDEX {0}_attachments_{0}_pk ON {0}_attachments ({0}_pk);", featureType.getName());
    switch (connProperties.getDbtype()) {
      case POSTGIS, SQLSERVER -> {
        return sql;
      }
      case ORACLE -> {
        return sql.toUpperCase(Locale.ROOT);
      }
      default ->
        throw new IllegalArgumentException(
            "Unsupported database type for attachments: " + connProperties.getDbtype());
    }
  }
}
