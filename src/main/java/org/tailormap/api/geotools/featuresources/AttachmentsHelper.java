/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.featuresources;

import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.dbcp.DelegatingConnection;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.jdbc.JDBCDataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.json.JDBCConnectionProperties;
import org.tailormap.api.viewer.model.AttachmentMetadata;

/** Helper class for managing the {@code <FT>_attachments} sidecar tables in JDBC DataStores. */
public final class AttachmentsHelper {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final Pattern NUMERIC_WITH_IDENTITY = Pattern.compile(
      "(?i)\\b(?:int|integer|bigint|smallint|numeric|decimal|number)(?:\\s*\\(\\s*\\d+(?:\\s*,\\s*\\d+)?\\s*\\))?\\s+identity\\b");

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

  private AttachmentsHelper() {
    // private constructor for utility class
  }

  private static String getPostGISCreateAttachmentsTableStatement(
      String tableName, String pkColumnName, String fkColumnType, String typeModifier, String schemaPrefix) {
    if (!schemaPrefix.isEmpty()) {
      schemaPrefix += ".";
    }
    return MessageFormat.format(
        """
CREATE TABLE IF NOT EXISTS {4}{0}_attachments (
{0}_pk          {2}{3}        NOT NULL REFERENCES {4}{0}({1}) ON DELETE CASCADE,
attachment_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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
    if (!schemaPrefix.isEmpty()) {
      schemaPrefix += ".";
    }
    return MessageFormat.format(
        """
IF OBJECT_ID(N''{4}{0}_attachments'', ''U'') IS NULL
BEGIN
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
END
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
   * Create attachment table and index for the given FeatureType. This will validate that any AttachmentAttribute has
   * a valid name.
   *
   * @param featureType the FeatureType to create the attachment table for
   * @throws IOException when creating the GeoTools datastore fails
   * @throws SQLException when executing the SQL statements fails
   * @throws IllegalArgumentException when the FeatureType is invalid
   */
  public static void createAttachmentTableForFeatureType(TMFeatureType featureType)
      throws IOException, SQLException, IllegalArgumentException {
    if (featureType == null
        || featureType.getSettings() == null
        || featureType.getSettings().getAttachmentAttributes() == null
        || featureType.getSettings().getAttachmentAttributes().isEmpty()) {
      throw new IllegalArgumentException("FeatureType "
          + (featureType != null ? featureType.getName() : "null")
          + " is invalid or has no attachment attributes defined in its settings");
    }
    // check if any attachment attribute names are empty or null
    featureType.getSettings().getAttachmentAttributes().stream()
        .filter(attachmentAttributeType -> (attachmentAttributeType.getAttributeName() == null
            || attachmentAttributeType.getAttributeName().isEmpty()))
        .findAny()
        .ifPresent(attachmentAttributeType -> {
          throw new IllegalArgumentException("FeatureType "
              + featureType.getName()
              + " has an attachment attribute with invalid (null or empty) attribute name");
        });

    logger.debug(
        "Creating attachment table for FeatureType: {} and attachment names {}",
        featureType.getName(),
        featureType.getSettings().getAttachmentAttributes());

    JDBCDataStore ds = null;
    try {
      ds = (JDBCDataStore) new JDBCFeatureSourceHelper().createDataStore(featureType.getFeatureSource());
      JdbcClient client = JdbcClient.create(ds.getDataSource());
      String sql = getCreateAttachmentsForFeatureTypeStatements(featureType, ds);
      logger.debug("About to create attachments table using statement:\n{}", sql);
      client.sql(sql).update();
      logger.info("Attachment table created for FeatureType: {}", featureType.getName());

      sql = getCreateAttachmentsIndexForFeatureTypeStatements(featureType, ds);
      logger.debug("About to create attachments table FK index using statement:\n{}", sql);
      client.sql(sql).update();
      logger.info("Attachment table FK index created for FeatureType: {}", featureType.getName());
    } finally {
      if (ds != null) {
        ds.dispose();
      }
    }
  }

  public static void dropAttachmentTableForFeatureType(TMFeatureType featureType) throws IOException, SQLException {
    JDBCDataStore ds = null;
    try {
      ds = (JDBCDataStore) new JDBCFeatureSourceHelper().createDataStore(featureType.getFeatureSource());
      String schemaPrefix = ds.getDatabaseSchema();
      if (!schemaPrefix.isEmpty()) {
        schemaPrefix += ".";
      }
      String dropSql = MessageFormat.format("DROP TABLE {1}{0}_attachments", featureType.getName(), schemaPrefix);
      logger.debug("About to drop attachments table using statement:\n{}", dropSql);
      JdbcClient.create(ds.getDataSource()).sql(dropSql).update();
      logger.info("Attachment table dropped for FeatureType: {}", featureType.getName());
    } finally {
      if (ds != null) {
        ds.dispose();
      }
    }
  }

  /**
   * Get the SQL statement to create the attachments table for the given feature type.
   *
   * @param featureType The feature type
   * @return The SQL statement
   * @throws IOException If an error connecting to the database occurs
   * @throws IllegalArgumentException If the database type is not supported
   */
  private static String getCreateAttachmentsForFeatureTypeStatements(
      TMFeatureType featureType, @NotNull JDBCDataStore ds)
      throws IOException, IllegalArgumentException, SQLException {

    String fkColumnType = null;
    int fkColumnSize = 0;
    AttributeDescriptor pkDescriptor =
        ds.getSchema(featureType.getName()).getDescriptor(featureType.getPrimaryKeyAttribute());

    try (Connection conn = ((DelegatingConnection) ds.getDataSource().getConnection()).getInnermostDelegate()) {
      DatabaseMetaData metaData = conn.getMetaData();
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

      // Final fallback to GeoTools nativeType from the attribute descriptor
      if (fkColumnType == null) {
        fkColumnType = (String) pkDescriptor.getUserData().get("org.geotools.jdbc.nativeTypeName");
      }
    }

    String typeModifier = "";
    if (fkColumnSize > 0) {
      typeModifier = getValidModifier(fkColumnType, fkColumnSize);
    }
    logger.debug(
        "Creating attachment table for feature type with primary key {} (native type: {}, meta type: {}, size:"
            + " {} (modifier: {}))",
        pkDescriptor.getLocalName(),
        fkColumnType,
        pkDescriptor.getUserData().get("org.geotools.jdbc.nativeTypeName"),
        fkColumnSize,
        typeModifier);

    JDBCConnectionProperties connProperties = featureType.getFeatureSource().getJdbcConnection();
    fkColumnType = getValidColumnType(fkColumnType, connProperties.getDbtype());
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

  private static String getValidColumnType(String columnType, JDBCConnectionProperties.DbtypeEnum dbtype) {
    if (dbtype.equals(JDBCConnectionProperties.DbtypeEnum.SQLSERVER)
        && NUMERIC_WITH_IDENTITY.matcher(columnType).find()) {
      // Remove IDENTITY keyword from numeric types as it is not supported in FK columns
      columnType = columnType.replaceAll("(?i)\\s+identity\\b", "");
    }

    return columnType;
  }

  private static String getValidModifier(String columnType, int fkColumnSize) {
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
  private static String getCreateAttachmentsIndexForFeatureTypeStatements(TMFeatureType featureType, JDBCDataStore ds)
      throws IllegalArgumentException {

    String schemaPrefix = ds.getDatabaseSchema();
    if (!schemaPrefix.isEmpty()) {
      schemaPrefix += ".";
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
            """
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE name = ''{0}_attachments_fk'' AND object_id = OBJECT_ID(N''{1}{0}_attachments''))
BEGIN
CREATE INDEX {0}_attachments_fk ON {1}{0}_attachments({0}_pk)
END
""",
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

  /** Convert UUID to byte array for storage in Oracle RAW(16). */
  private static byte[] asBytes(UUID uuid) {
    ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
    bb.putLong(uuid.getMostSignificantBits());
    bb.putLong(uuid.getLeastSignificantBits());
    return bb.array();
  }

  public static AttachmentMetadata insertAttachment(
      TMFeatureType featureType, AttachmentMetadata attachment, Object primaryKey, byte[] fileData)
      throws IOException, SQLException {

    // create uuid here so we don't have to deal with DB-specific returning/generated key syntax
    attachment.setAttachmentId(UUID.randomUUID());
    attachment.setAttachmentSize((long) fileData.length);
    attachment.createdAt(OffsetDateTime.now(ZoneId.of("UTC")));
    attachment.setCreatedBy(
        SecurityContextHolder.getContext().getAuthentication().getName());

    logger.debug(
        "Adding attachment {} for feature {}:{}, type {}: {} (bytes: {})",
        attachment.getAttachmentId(),
        featureType.getName(),
        primaryKey,
        attachment.getMimeType(),
        attachment,
        fileData.length);

    JDBCDataStore ds = null;
    try {
      ds = (JDBCDataStore) new JDBCFeatureSourceHelper().createDataStore(featureType.getFeatureSource());

      String insertSql = MessageFormat.format(
          """
INSERT INTO {1}{0}_attachments (
{0}_pk, attachment_id, file_name, attribute_name, description, attachment, attachment_size,
mime_type, created_at, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
""",
          featureType.getName(), ds.getDatabaseSchema().isEmpty() ? "" : ds.getDatabaseSchema() + ".");

      logger.debug("Insert attachment SQL: {}", insertSql);
      try (Connection conn = ds.getDataSource().getConnection();
          PreparedStatement stmt = conn.prepareStatement(insertSql)) {

        stmt.setObject(1, primaryKey);
        if (featureType
            .getFeatureSource()
            .getJdbcConnection()
            .getDbtype()
            .equals(JDBCConnectionProperties.DbtypeEnum.ORACLE)) {

          stmt.setBytes(2, asBytes(attachment.getAttachmentId()));
        } else {
          stmt.setObject(2, attachment.getAttachmentId());
        }
        stmt.setString(3, attachment.getFileName());
        stmt.setString(4, attachment.getAttributeName());
        stmt.setString(5, attachment.getDescription());
        stmt.setBytes(6, fileData);
        stmt.setLong(7, fileData.length);
        stmt.setString(8, attachment.getMimeType());
        stmt.setTimestamp(
            9, java.sql.Timestamp.from(attachment.getCreatedAt().toInstant()));
        stmt.setString(10, attachment.getCreatedBy());

        stmt.executeUpdate();

        return attachment;
      }
    } finally {
      if (ds != null) {
        ds.dispose();
      }
    }
  }

  public static void deleteAttachment(UUID attachmentId, TMFeatureType featureType) throws IOException, SQLException {
    String deleteSql = MessageFormat.format(
        """
DELETE FROM {0}_attachments WHERE attachment_id = ?
""", featureType.getName());
    JDBCDataStore ds = null;
    try {
      ds = (JDBCDataStore) new JDBCFeatureSourceHelper().createDataStore(featureType.getFeatureSource());
      try (Connection conn = ds.getDataSource().getConnection();
          PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
        if (featureType
            .getFeatureSource()
            .getJdbcConnection()
            .getDbtype()
            .equals(JDBCConnectionProperties.DbtypeEnum.ORACLE)) {
          stmt.setBytes(1, asBytes(attachmentId));
        } else {
          stmt.setObject(1, attachmentId);
        }

        stmt.executeUpdate();
      }
    } finally {
      if (ds != null) {
        ds.dispose();
      }
    }
  }

  public static List<AttachmentMetadata> listAttachmentsForFeature(TMFeatureType featureType, Object primaryKey)
      throws IOException, SQLException {

    List<AttachmentMetadata> attachments = new ArrayList<>();
    JDBCDataStore ds = null;
    try {
      ds = (JDBCDataStore) new JDBCFeatureSourceHelper().createDataStore(featureType.getFeatureSource());
      String querySql = MessageFormat.format(
          """
SELECT
{0}_pk,
attachment_id,
file_name,
attribute_name,
description,
attachment_size,
mime_type,
created_at,
created_by
FROM {1}{0}_attachments WHERE {0}_pk = ?
""",
          featureType.getName(), ds.getDatabaseSchema().isEmpty() ? "" : ds.getDatabaseSchema() + ".");
      try (Connection conn = ds.getDataSource().getConnection();
          PreparedStatement stmt = conn.prepareStatement(querySql)) {

        stmt.setObject(1, primaryKey);

        try (ResultSet rs = stmt.executeQuery()) {
          while (rs.next()) {
            AttachmentMetadata a = getAttachmentMetadata(rs);
            attachments.add(a);
          }
        }
      }
    } finally {
      if (ds != null) {
        ds.dispose();
      }
    }
    return attachments;
  }

  public static AttachmentWithBinary getAttachment(TMFeatureType featureType, UUID attachmentId)
      throws IOException, SQLException {

    JDBCDataStore ds = null;
    try {
      byte[] attachment;
      ds = (JDBCDataStore) new JDBCFeatureSourceHelper().createDataStore(featureType.getFeatureSource());
      String querySql = MessageFormat.format(
          "SELECT attachment, attachment_size, mime_type, file_name FROM {1}{0}_attachments WHERE attachment_id = ?",
          featureType.getName(), ds.getDatabaseSchema().isEmpty() ? "" : ds.getDatabaseSchema() + ".");
      try (Connection conn = ds.getDataSource().getConnection();
          PreparedStatement stmt = conn.prepareStatement(querySql)) {

        if (featureType
            .getFeatureSource()
            .getJdbcConnection()
            .getDbtype()
            .equals(JDBCConnectionProperties.DbtypeEnum.ORACLE)) {
          stmt.setBytes(1, asBytes(attachmentId));
        } else {
          stmt.setObject(1, attachmentId);
        }

        try (ResultSet rs = stmt.executeQuery()) {
          if (rs.next()) {
            attachment = rs.getBytes("attachment");
            AttachmentMetadata a = new AttachmentMetadata();
            long size = rs.getLong("attachment_size");
            if (!rs.wasNull()) {
              a.setAttachmentSize(size);
            }
            a.setMimeType(rs.getString("mime_type"));
            a.setFileName(rs.getString("file_name"));
            return new AttachmentWithBinary(
                a, ByteBuffer.wrap(attachment).asReadOnlyBuffer());
          } else {
            return null;
          }
        }
      }
    } finally {
      if (ds != null) {
        ds.dispose();
      }
    }
  }

  /**
   * List attachments for multiple features grouped by their IDs. <br>
   * <strong>NOTE</strong>: the featurePKs list should contain {@link Comparable} objects (e.g. no {@code byte[]}), as
   * these are used as map keys. E.g. {@code byte[]} is converted to {@code ByteBuffer}. Use
   * {@link #checkAndMakeFeaturePkComparable(Object)} to convert feature primary keys if necessary.
   *
   * @param featureType the feature type
   * @param featurePKs the feature primary keys
   * @return map of feature ID to list of attachments
   * @throws IOException when an IO error occurs connecting to the database
   */
  public static Map<@NotNull Comparable<?>, List<AttachmentMetadata>> listAttachmentsForFeaturesByFeatureId(
      TMFeatureType featureType, List<Comparable<?>> featurePKs) throws IOException {
    List<AttachmentMetadataListItem> attachments = new ArrayList<>();
    if (featurePKs == null || featurePKs.isEmpty()) {
      return new HashMap<>();
    }

    JDBCDataStore ds = null;
    try {
      ds = (JDBCDataStore) new JDBCFeatureSourceHelper().createDataStore(featureType.getFeatureSource());
      String querySql = MessageFormat.format(
          """
SELECT
{0}_pk,
attachment_id,
file_name,
attribute_name,
description,
attachment_size,
mime_type,
created_at,
created_by
FROM {2}{0}_attachments WHERE {0}_pk IN ( {1} )
""",
          featureType.getName(),
          String.join(", ", featurePKs.stream().map(id -> "?").toArray(String[]::new)),
          ds.getDatabaseSchema().isEmpty() ? "" : ds.getDatabaseSchema() + ".");

      try (Connection conn = ds.getDataSource().getConnection();
          PreparedStatement stmt = conn.prepareStatement(querySql)) {

        Object firstPK = featurePKs.getFirst();
        boolean isUUID = firstPK instanceof UUID;
        boolean isByteBuffer = firstPK instanceof ByteBuffer;

        switch (featureType.getFeatureSource().getJdbcConnection().getDbtype()) {
          case ORACLE -> {
            for (int i = 0; i < featurePKs.size(); i++) {
              if (isUUID) {
                // Oracle (RAW(16)): Comparisons are possible, but the values in the IN list must be
                // correctly formatted binary literals (hextoraw('...')).
                stmt.setBytes(i + 1, asBytes((UUID) featurePKs.get(i)));
              } else if (isByteBuffer) {
                // unwrap ByteBuffer to byte[] for the query
                stmt.setBytes(i + 1, ((ByteBuffer) featurePKs.get(i)).array());
              } else {
                stmt.setObject(i + 1, featurePKs.get(i));
              }
            }
          }
          case SQLSERVER -> {
            for (int i = 0; i < featurePKs.size(); i++) {
              if (isUUID) {
                // use uppercase string representation for SQL Server UNIQUEIDENTIFIER
                stmt.setString(
                    i + 1, featurePKs.get(i).toString().toUpperCase(Locale.ROOT));
              } else {
                stmt.setObject(i + 1, featurePKs.get(i));
              }
            }
          }
          case POSTGIS -> {
            for (int i = 0; i < featurePKs.size(); i++) {
              stmt.setObject(i + 1, featurePKs.get(i));
            }
          }
          default ->
            throw new UnsupportedOperationException("Unsupported database type: "
                + featureType
                    .getFeatureSource()
                    .getJdbcConnection()
                    .getDbtype());
        }

        try (ResultSet rs = stmt.executeQuery()) {
          while (rs.next()) {
            AttachmentMetadata a = getAttachmentMetadata(rs);
            Object keyObject = rs.getObject(1);
            Comparable<?> comparableKey;

            if (isUUID
                && featureType
                    .getFeatureSource()
                    .getJdbcConnection()
                    .getDbtype()
                    .equals(JDBCConnectionProperties.DbtypeEnum.ORACLE)) {
              // convert RAW(16) back to UUID
              byte[] rawBytes = rs.getBytes(1);
              ByteBuffer bb = ByteBuffer.wrap(rawBytes);
              comparableKey = new UUID(bb.getLong(), bb.getLong());
            } else if (isUUID
                && featureType
                    .getFeatureSource()
                    .getJdbcConnection()
                    .getDbtype()
                    .equals(JDBCConnectionProperties.DbtypeEnum.SQLSERVER)) {
              // convert uppercase string back to UUID
              comparableKey = UUID.fromString(rs.getString(1));
            } else if (isByteBuffer) {
              // we need to use a key that is comparable, so convert byte[] to ByteBuffer
              assert keyObject instanceof byte[];
              comparableKey = ByteBuffer.wrap((byte[]) keyObject);
            } else {
              // Most other returned PK types (String, Number, UUID) implement Comparable
              comparableKey = (Comparable<?>) keyObject;
            }
            attachments.add(new AttachmentMetadataListItem(comparableKey, a));
          }
        }
      } catch (SQLException ex) {
        logger.error("Failed to get attachments for %s".formatted(featureType.getName()), ex);
      }
    } finally {
      if (ds != null) {
        ds.dispose();
      }
    }
    logger.debug(
        "Found {} attachments for {} features (features: {}, attachments: {})",
        attachments.size(),
        featurePKs.size(),
        featurePKs,
        attachments.toArray());

    return attachments.stream()
        .collect(Collectors.groupingBy(
            AttachmentMetadataListItem::key,
            Collectors.mapping(AttachmentMetadataListItem::value, Collectors.toList())));
  }

  /**
   * Check if the given feature primary key is Comparable, and convert it if necessary (e.g. byte[] to ByteBuffer). We
   * need the key to be Comparable as it is used as map key in {@link AttachmentMetadataListItem}. Currently supported
   * types are:
   *
   * <ul>
   *   <li>Comparable (String, Number, UUID, etc.) - returned as is
   *   <li>byte[] - converted to ByteBuffer
   * </ul>
   *
   * Otherwise an IllegalArgumentException is thrown.
   *
   * @param featurePK the feature primary key (NOT {@code null}) to check
   * @return the Comparable feature primary key (NOT {@code null})
   * @throws IllegalArgumentException when the feature primary key is null, not Comparable, and no mapping is
   *     specified
   */
  public static @NotNull Comparable<?> checkAndMakeFeaturePkComparable(@NotNull Object featurePK) {
    if (featurePK instanceof Comparable<?>) {
      return (Comparable<?>) featurePK;
    } else if (featurePK instanceof byte[] pkBytes) {
      // convert byte[] to ByteBuffer which is Comparable
      return ByteBuffer.wrap(pkBytes);
    } else {
      throw new IllegalArgumentException("Unexpected non-Comparable primary key type from database: "
          + (featurePK != null ? featurePK.getClass().getName() : "null"));
    }
  }

  private static AttachmentMetadata getAttachmentMetadata(ResultSet rs) throws SQLException {
    AttachmentMetadata a = new AttachmentMetadata();
    // attachment_id (handle UUID, RAW(16) as byte[] or string)
    Object idObj = rs.getObject("attachment_id");
    if (idObj instanceof UUID u) {
      a.setAttachmentId(u);
    } else if (idObj instanceof byte[] b) {
      ByteBuffer bb = ByteBuffer.wrap(b);
      a.setAttachmentId(new UUID(bb.getLong(), bb.getLong()));
    } else {
      String s = rs.getString("attachment_id");
      if (s != null && !s.isEmpty()) {
        a.setAttachmentId(UUID.fromString(s));
      }
    }
    a.setFileName(rs.getString("file_name"));
    a.setAttributeName(rs.getString("attribute_name"));
    a.setDescription(rs.getString("description"));
    long size = rs.getLong("attachment_size");
    if (!rs.wasNull()) {
      a.setAttachmentSize(size);
    }
    a.setMimeType(rs.getString("mime_type"));
    java.sql.Timestamp ts = rs.getTimestamp("created_at");
    if (ts != null) {
      a.setCreatedAt(OffsetDateTime.ofInstant(ts.toInstant(), ZoneId.of("UTC")));
    }
    a.setCreatedBy(rs.getString("created_by"));
    return a;
  }

  public record AttachmentWithBinary(
      @NotNull AttachmentMetadata attachmentMetadata, @NotNull ByteBuffer attachment) {}

  private record AttachmentMetadataListItem(@NotNull Comparable<?> key, @NotNull AttachmentMetadata value) {}
}
