/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.drawing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.postgresql.util.PGobject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SimplePropertyRowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.viewer.model.Drawing;

import java.lang.invoke.MethodHandles;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service for managing drawings.
 *
 * <p>This service provides methods for creating, updating, reading, and deleting drawings. Any call can throw a
 * {@link ResponseStatusException} if the user is not allowed to perform the operation.
 */
@Service
public class DrawingService {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final JdbcClient jdbcClient;
  private final RowMapper<Drawing> drawingRowMapper;
  private final ObjectMapper objectMapper;

  public DrawingService(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    this.jdbcClient = jdbcClient;
    this.objectMapper = objectMapper;

    final GenericConversionService conversionService = new GenericConversionService();
    DefaultConversionService.addDefaultConverters(conversionService);

    conversionService.addConverter(new Converter<String, Drawing.AccessEnum>() {
      @Override
      public Drawing.AccessEnum convert(@NonNull String source) {
        return Drawing.AccessEnum.fromValue(source);
      }
    });

    conversionService.addConverter(new Converter<PGobject, Map<String, Object>>() {
      @Override
      @SuppressWarnings("unchecked")
      public Map<String, Object> convert(@NonNull PGobject source) {
        try {
          return objectMapper.readValue(source.getValue(), Map.class);
        } catch (JsonProcessingException e) {
          throw new IllegalArgumentException("Failed to convert PGobject to Map", e);
        }
      }
    });

    drawingRowMapper = new SimplePropertyRowMapper<>(Drawing.class, conversionService) {
      @Override
      @NonNull public Drawing mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
        return super.mapRow(rs, rowNum);
      }
    };
  }

  /**
   * Create a new drawing.
   *
   * @param drawing the drawing to create
   * @param authentication the current user
   * @return the created drawing
   */
  @Transactional
  public Drawing createDrawing(@NonNull Drawing drawing, @NonNull Authentication authentication)
      throws JsonProcessingException {
    logger.warn("createDrawing for {}", drawing);

    canCreateDrawing(authentication);

    logger.debug(
        "creating new drawing: {}, domainData {}, createdAt {}",
        drawing,
        objectMapper.writeValueAsString(drawing.getDomainData()),
        OffsetDateTime.now(ZoneId.systemDefault()));

    Drawing storedDrawing = jdbcClient
        .sql(
            """
INSERT INTO data.drawing (name, description, domaindata, access, created_at, created_by,srid)
VALUES (?, ?, ?::jsonb, ?, ?, ?, ?) RETURNING *
""")
        .param(drawing.getName())
        .param(drawing.getDescription())
        .param(objectMapper.writeValueAsString(drawing.getDomainData()))
        .param(drawing.getAccess().getValue())
        .param(OffsetDateTime.now(ZoneId.systemDefault()))
        .param(authentication.getName())
        .param(drawing.getSrid())
        .query(drawingRowMapper)
        .single();

    logger.debug("stored new drawing: {}", storedDrawing);

    if (drawing.getFeatureCollection() != null) {
      List<JsonNode> storedFeatures = jdbcClient
          .sql(
              """
WITH jsonData AS (SELECT ?::json AS featureCollection)
INSERT INTO data.drawing_geometry (drawing_id, geometry, properties)
SELECT ?::uuid AS drawing_id,
ST_SetSRID(ST_GeomFromGeoJSON(feature ->> 'geometry'), ?) AS geometry,
feature -> 'properties' AS properties
FROM (SELECT json_array_elements(featureCollection -> 'features') AS feature
FROM jsonData)
AS f
RETURNING
-- since we cannot use aggregate functions in a returning clause, we will return a list of geojson
-- features and aggregate them into a featureCollection in the next step
ST_AsGeoJSON(data.drawing_geometry.*, geom_column =>'geometry', id_column => 'id')::json;
""")
          .param(objectMapper.writeValueAsString(drawing.getFeatureCollection()))
          .param(storedDrawing.getId())
          .param(drawing.getSrid())
          .query(new RowMapper<JsonNode>() {
            @Override
            public JsonNode mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
              try {
                JsonNode jsonNode = objectMapper.readTree(rs.getString(1));
                // merge properties with nested properties (because we have a jsonb column called
                // "properties")
                final ObjectNode properties = (ObjectNode) jsonNode.get("properties");
                JsonNode nestedProperties = properties.get("properties");
                if (nestedProperties != null) {
                  nestedProperties.properties().stream()
                      .iterator()
                      .forEachRemaining(entry -> {
                        properties.putIfAbsent(entry.getKey(), entry.getValue());
                      });
                }
                properties.remove("properties");
                return jsonNode;
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            }
          })
          .list();

      ObjectNode featureCollection = objectMapper
          .createObjectNode()
          .put("type", "FeatureCollection")
          .set("features", objectMapper.createArrayNode().addAll(storedFeatures));
      logger.debug("stored features: {}", featureCollection);
      storedDrawing.setFeatureCollection(featureCollection);
    }
    return storedDrawing;
  }

  /**
   * Update an existing drawing.
   *
   * @param drawing the drawing to create
   * @param authentication the current user
   * @return the created drawing
   */
  @Transactional
  public Drawing updateDrawing(@NonNull Drawing drawing, @NonNull Authentication authentication) {
    canSaveOrDeleteDrawing(drawing, authentication);

    final Drawing storedDrawing = getDrawing(drawing.getId(), authentication, false, drawing.getSrid())
        .orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Drawing has been deleted by another user"));

    if (drawing.getVersion() < storedDrawing.getVersion()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Drawing has been updated by another user");
    }
    drawing.setVersion(drawing.getVersion() + 1);

    return jdbcClient
        .sql(
            """
UPDATE data.drawing SET
id=:id,
name=:name,
description=:description,
domaindata=:domainData::jsonb,
access=:access,
created_by=:createdBy,
created_at=:createdAt,
updated_by=:updatedBy,
updated_at=:updatedAt,
srid=:srid,
version=:version
WHERE id = :id RETURNING *""")
        .param("id", drawing.getId())
        .param("name", drawing.getName())
        .param("description", drawing.getDescription())
        .param("domainData", drawing.getDomainData())
        .param("access", drawing.getAccess().getValue())
        .param("createdBy", drawing.getCreatedBy())
        .param("createdAt", drawing.getCreatedAt())
        .param("updatedBy", authentication.getName())
        .param("updatedAt", OffsetDateTime.now(ZoneId.systemDefault()))
        .param("srid", drawing.getSrid())
        .param("version", drawing.getVersion())
        .query(drawingRowMapper)
        .single();
  }

  /**
   * Get all drawings for the current user.
   *
   * @param authentication the current user
   * @return the drawings
   */
  public Set<Drawing> getDrawingsForUser(@NonNull Authentication authentication) {
    // TODO: this query is not correct/complete; it should be fixed to:
    //    - take access level into account and
    //    - possibly cater for anonymous users
    return jdbcClient
        .sql("SELECT * FROM data.drawing WHERE created_by = ? OR updated_by = ?")
        .param(1, authentication.getName())
        .param(2, authentication.getName())
        .query(drawingRowMapper)
        .set();
  }

  /**
   * Get a drawing only — no geometry data — by its ID.
   *
   * @param drawingId the ID of the drawing
   * @param authentication the current user
   * @return the — thinly populated — drawing
   */
  public Optional<Drawing> getDrawing(@NonNull UUID drawingId, @NonNull Authentication authentication) {
    return this.getDrawing(drawingId, authentication, false, 0);
  }

  /**
   * Get a complete drawing by its ID.
   *
   * @param drawingId the ID of the drawing
   * @param authentication the current user
   * @param withGeometries whether to fetch the geometries for the drawing
   * @param requestedSrid the SRID to return the geometries in
   * @return the complete drawing
   */
  public Optional<Drawing> getDrawing(
      @NonNull UUID drawingId,
      @NonNull Authentication authentication,
      boolean withGeometries,
      int requestedSrid) {
    Optional<Drawing> drawing =
        jdbcClient
            .sql("SELECT * FROM data.drawing WHERE id = ?")
            .param(1, drawingId)
            .query(drawingRowMapper)
            .stream()
            .findFirst();

    logger.debug("found drawing: {}", drawing);

    drawing.ifPresent(d -> {
      canReadDrawing(d, authentication);
      if (withGeometries) {
        // TODO fetch featureCollection for drawing
        logger.trace("TODO fetch featureCollection for drawing {} with srid {}", drawingId, requestedSrid);
        Object featureCollection = null;
        d.setFeatureCollection(featureCollection);
      }
    });

    return drawing;
  }

  /**
   * Delete a drawing by its ID.
   *
   * @param drawingId the ID of the drawing
   * @param authentication the current user
   */
  public void deleteDrawing(@NonNull UUID drawingId, @NonNull Authentication authentication) {
    canSaveOrDeleteDrawing(
        getDrawing(drawingId, authentication)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Drawing not found")),
        authentication);

    jdbcClient.sql("DELETE FROM data.drawing WHERE id = ?").param(drawingId).update();
  }

  /**
   * Check if the current user can read the drawing. If not, throw an unauthorized exception.
   *
   * @param authentication the current user
   * @throws ResponseStatusException if the user is not allowed to create a drawing
   */
  private void canCreateDrawing(@NonNull Authentication authentication) throws ResponseStatusException {
    if ((authentication instanceof AnonymousAuthenticationToken)) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Insufficient permissions to create new drawing");
    }
    // TODO check if this user is allowed to create/add drawings using the current authentication
  }

  /**
   * Check if the current user can read the drawing. If not, throw an unauthorized exception.
   *
   * @param drawing the drawing to check
   * @param authentication the current user
   * @throws ResponseStatusException if the user is not allowed to read the drawing
   */
  private void canReadDrawing(@NonNull Drawing drawing, @NonNull Authentication authentication)
      throws ResponseStatusException {
    // TODO validate this method
    boolean isAuthenticated = !(authentication instanceof AnonymousAuthenticationToken);

    boolean canRead =
        switch (drawing.getAccess()) {
          case PRIVATE -> isAuthenticated && Objects.equals(authentication.getName(), drawing.getCreatedBy());
          case SHARED -> isAuthenticated;
          case PUBLIC -> true;
        };

    if (!canRead) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Insufficient permissions to access drawing");
    }
  }

  /**
   * Check if the current user can save the drawing. If not, throw an unauthorized exception.
   *
   * @param drawing the drawing to check
   * @param authentication the current user
   * @throws ResponseStatusException if the user cannot read the drawing
   */
  private void canSaveOrDeleteDrawing(@NonNull Drawing drawing, @NonNull Authentication authentication)
      throws ResponseStatusException {
    // TODO validate this method
    boolean isAuthenticated = !(authentication instanceof AnonymousAuthenticationToken);

    if (!isAuthenticated) {
      // Only authenticated users can save drawings, irrelevant of drawing access level
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Insufficient permissions to save drawing");
    }

    boolean canSave =
        switch (drawing.getAccess()) {
          case PRIVATE -> drawing.getCreatedBy().equals(authentication.getName());
          case SHARED, PUBLIC -> true;
        };

    if (!canSave) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Insufficient permissions to save drawing");
    }
  }
}
