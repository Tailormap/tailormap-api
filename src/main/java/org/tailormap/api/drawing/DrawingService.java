/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.drawing;

import static org.tailormap.api.persistence.helper.AdminAdditionalPropertyHelper.KEY_DRAWINGS_ADMIN;
import static org.tailormap.api.persistence.helper.AdminAdditionalPropertyHelper.KEY_DRAWINGS_READ_ALL;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.invoke.MethodHandles;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
import org.tailormap.api.persistence.json.AdminAdditionalProperty;
import org.tailormap.api.security.TailormapUserDetails;
import org.tailormap.api.viewer.model.Drawing;

/**
 * Service for managing drawings.
 *
 * <p>This service provides methods for creating, updating, reading, and deleting drawings and persisting these
 * operations in the data schema of the tailormap database. Any call can throw a {@link ResponseStatusException} if the
 * user is not allowed to perform the operation.
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

    canCreateDrawing(authentication);

    logger.trace(
        "creating new drawing: {}, domainData {}, createdAt {}",
        drawing,
        objectMapper.writeValueAsString(drawing.getDomainData()),
        OffsetDateTime.now(ZoneId.systemDefault()));

    Drawing storedDrawing = jdbcClient
        .sql(
            """
INSERT INTO data.drawing (name, description, domain_data, access, created_at, created_by,srid)
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

    if (drawing.getFeatureCollection() != null) {
      ObjectNode featureCollection = insertGeoJsonFeatureCollection(
          storedDrawing.getId(),
          drawing.getSrid(),
          objectMapper.writeValueAsString(drawing.getFeatureCollection()));
      storedDrawing.setFeatureCollection(featureCollection);
    }

    logger.trace("stored new drawing: {}", storedDrawing);
    return storedDrawing;
  }

  private ObjectNode insertGeoJsonFeatureCollection(UUID drawingId, int srid, String featureCollectionToStore)
      throws JsonProcessingException {
    List<JsonNode> storedFeatures = jdbcClient
        .sql(
            """
WITH jsonData AS (SELECT :featureCollectionToStore::json AS featureCollection)
INSERT INTO data.drawing_feature (drawing_id, geometry, properties)
SELECT :drawingId::uuid AS drawing_id,
ST_SetSRID(ST_GeomFromGeoJSON(feature ->> 'geometry'), :srid) AS geometry,
feature -> 'properties' AS properties
FROM (SELECT json_array_elements(featureCollection -> 'features') AS feature
FROM jsonData)
AS f
RETURNING
-- since we cannot use aggregate functions in a returning clause, we will return a list of geojson
-- features and aggregate them into a featureCollection in the next step
ST_AsGeoJSON(data.drawing_feature.*, geom_column =>'geometry', id_column => 'id')::json;
""")
        .param("featureCollectionToStore", featureCollectionToStore)
        .param("drawingId", drawingId)
        .param("srid", srid)
        .query(new RowMapper<JsonNode>() {
          @Override
          public JsonNode mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            try {
              JsonNode jsonNode = objectMapper.readTree(rs.getString(1));
              // merge/un-nest properties with nested properties, because we have a jsonb
              // column
              // called "properties" and we are using the `ST_AsGeoJSON(::record,...)`
              // function
              final ObjectNode properties = (ObjectNode) jsonNode.get("properties");
              JsonNode nestedProperties = properties.get("properties");
              if (nestedProperties != null) {
                nestedProperties.properties().stream()
                    .iterator()
                    .forEachRemaining(
                        entry -> properties.putIfAbsent(entry.getKey(), entry.getValue()));
              }
              properties.remove("properties");
              return jsonNode;
            } catch (JsonProcessingException e) {
              throw new RuntimeException(e);
            }
          }
        })
        .list();

    return objectMapper
        .createObjectNode()
        .put("type", "FeatureCollection")
        .set("features", objectMapper.createArrayNode().addAll(storedFeatures));
  }

  /**
   * Update an existing drawing.
   *
   * @param drawing the drawing to create
   * @param authentication the current user
   * @return the created drawing
   */
  @Transactional
  public Drawing updateDrawing(@NonNull Drawing drawing, @NonNull Authentication authentication)
      throws JsonProcessingException {

    canSaveOrDeleteDrawing(drawing, authentication);

    logger.trace(
        "updating drawing: {}, domainData {}, updatedAt {}",
        drawing,
        objectMapper.writeValueAsString(drawing.getDomainData()),
        OffsetDateTime.now(ZoneId.systemDefault()));

    final Drawing oldDrawing = getDrawing(drawing.getId(), authentication)
        .orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Drawing has been deleted by another user"));

    if (drawing.getVersion() < oldDrawing.getVersion()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Drawing has been updated by another user");
    }
    drawing.setVersion(drawing.getVersion() + 1);

    Drawing updatedDrawing = jdbcClient
        .sql(
            """
UPDATE data.drawing SET
id=:id,
name=:name,
description=:description,
domain_data=:domainData::jsonb,
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
        .param("domainData", objectMapper.writeValueAsString(drawing.getDomainData()))
        .param("access", drawing.getAccess().getValue())
        .param("createdBy", drawing.getCreatedBy())
        .param("createdAt", drawing.getCreatedAt())
        .param("updatedBy", authentication.getName())
        .param("updatedAt", OffsetDateTime.now(ZoneId.systemDefault()))
        .param("srid", drawing.getSrid())
        .param("version", drawing.getVersion())
        .query(drawingRowMapper)
        .single();

    // delete even if drawing.getFeatureCollection()==null, because all features could have been
    // removed,
    // afterwards (re)insert the featureCollection
    jdbcClient
        .sql("DELETE FROM data.drawing_feature WHERE drawing_id = ?")
        .param(drawing.getId())
        .update();

    if (drawing.getFeatureCollection() != null) {
      ObjectNode featureCollection = insertGeoJsonFeatureCollection(
          drawing.getId(),
          drawing.getSrid(),
          objectMapper.writeValueAsString(drawing.getFeatureCollection()));
      updatedDrawing.setFeatureCollection(featureCollection);
    }

    logger.trace("stored updated drawing: {}", updatedDrawing);
    return updatedDrawing;
  }

  /**
   * Get all drawings for the current user.
   *
   * @param authentication the current user
   * @return the drawings, a possibly empty set
   */
  public Set<Drawing> getDrawingsForUser(Authentication authentication) throws ResponseStatusException {
    if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
      return Set.of();
    }
    return jdbcClient.sql("SELECT * FROM data.drawing").query(drawingRowMapper).set().stream()
        .filter(d -> {
          try {
            canReadDrawing(d, authentication);
            return true;
          } catch (ResponseStatusException e) {
            return false;
          }
        })
        .sorted(Comparator.comparing(Drawing::getCreatedAt))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Get a drawing only — no geometry data — by its ID.
   *
   * @param drawingId the ID of the drawing
   * @param authentication the current user
   * @return the — thinly populated — drawing
   */
  @SuppressWarnings("SpringTransactionalMethodCallsInspection")
  public Optional<Drawing> getDrawing(@NonNull UUID drawingId, @NonNull Authentication authentication) {
    return this.getDrawing(drawingId, authentication, false, 0);
  }

  /**
   * Get a complete drawing by its ID with GeoJSON geometries in the requested srid.
   *
   * @param drawingId the ID of the drawing
   * @param authentication the current user
   * @param withGeometries whether to fetch the geometries for the drawing
   * @param requestedSrid the SRID to return the geometries in
   * @return the complete drawing
   */
  @Transactional
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

    drawing.ifPresent(d -> {
      // check if the user is allowed to read the drawing
      canReadDrawing(d, authentication);

      d.setSrid(requestedSrid);
      if (withGeometries) {
        d.setFeatureCollection(getFeatureCollection(drawingId, requestedSrid));
      }
    });

    return drawing;
  }

  /**
   * Retrieve the feature collection as GeoJSON for a drawing.
   *
   * @param drawingId the ID of the drawing
   * @param srid the SRID to return the geometries in
   * @return the feature collection as GeoJSON
   */
  private JsonNode getFeatureCollection(UUID drawingId, int srid) {
    return jdbcClient
        .sql(
            """
SELECT row_to_json(featureCollection) from (
SELECT
'FeatureCollection' AS type,
array_to_json(array_agg(feature)) AS features FROM (
SELECT
'Feature' AS type,
id as id,
ST_ASGeoJSON(ST_Transform(geomTable.geometry, :srid))::json AS geometry,
row_to_json((SELECT l from (SELECT id, drawing_id, properties) AS l)) AS properties
FROM data.drawing_feature AS geomTable WHERE drawing_id = :drawingId::uuid) AS feature) AS featureCollection
""")
        .param("drawingId", drawingId)
        .param("srid", srid)
        .query(new RowMapper<JsonNode>() {
          @Override
          public JsonNode mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            try {
              JsonNode jsonNode = objectMapper.readTree(rs.getString(1));
              // merge/un-nest properties with nested properties, because we have a jsonb column
              // called "properties" and we are using the `ST_AsGeoJSON(::record,...)` function
              ArrayNode features = (ArrayNode) jsonNode.get("features");
              features.elements().forEachRemaining(feature -> {
                ObjectNode properties = (ObjectNode) feature.get("properties");
                JsonNode nestedProperties = properties.get("properties");
                if (nestedProperties != null) {
                  nestedProperties
                      .fields()
                      .forEachRemaining(
                          entry -> properties.putIfAbsent(entry.getKey(), entry.getValue()));
                }
                properties.remove("properties");
              });
              return jsonNode;
            } catch (JsonProcessingException e) {
              throw new RuntimeException(e);
            }
          }
        })
        .single();
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
   * Check if the current user can create a drawing. If not, throw an unauthorized exception.
   *
   * @param authentication the current user
   * @throws ResponseStatusException if the user is not allowed to create a drawing
   */
  private void canCreateDrawing(@NonNull Authentication authentication) throws ResponseStatusException {
    if ((authentication instanceof AnonymousAuthenticationToken)) {
      throw new ResponseStatusException(
          HttpStatus.UNAUTHORIZED, "Insufficient permissions to create new drawing");
    }
    // TODO check if this user is allowed to create/add drawings using additional properties,
    // currently none are
    //  defined
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
    boolean isAuthenticated = !(authentication instanceof AnonymousAuthenticationToken);
    boolean canRead =
        switch (drawing.getAccess()) {
          case PRIVATE -> {
            if (isAuthenticated) {
              if (Objects.equals(authentication.getName(), drawing.getCreatedBy())) {
                // is drawing owner
                yield true;
              }
              if (authentication.getPrincipal() instanceof TailormapUserDetails userDetails) {
                // check if the user has either the `drawings-admin` or `drawings-read-all` property
                // set
                for (AdminAdditionalProperty ap : userDetails.getAdditionalProperties()) {
                  if (ap.getKey().equals(KEY_DRAWINGS_ADMIN)
                      || ap.getKey().equals(KEY_DRAWINGS_READ_ALL)) {
                    if ("true".equals(ap.getValue().toString())) {
                      yield true;
                    }
                  }
                }
              }
            }
            yield false;
          }
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
   * @throws ResponseStatusException if the user is not allowed to save/delete the drawing
   */
  private void canSaveOrDeleteDrawing(@NonNull Drawing drawing, @NonNull Authentication authentication)
      throws ResponseStatusException {
    if (authentication instanceof AnonymousAuthenticationToken) {
      // Only authenticated users can save drawings, irrelevant of drawing access level
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Insufficient permissions to save drawing");
    }

    boolean canSave =
        switch (drawing.getAccess()) {
          case PRIVATE -> {
            if (Objects.equals(authentication.getName(), drawing.getCreatedBy())) {
              // is drawing owner
              yield true;
            }
            if (authentication.getPrincipal() instanceof TailormapUserDetails userDetails) {
              // check if the user has the drawings-admin property set
              for (AdminAdditionalProperty ap : userDetails.getAdditionalProperties()) {
                if (ap.getKey().equals(KEY_DRAWINGS_ADMIN)
                    && "true".equals(ap.getValue().toString())) {
                  yield true;
                }
              }
            }
            yield false;
          }
          case SHARED, PUBLIC -> true;
        };

    if (!canSave) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Insufficient permissions to save drawing");
    }
  }
}
