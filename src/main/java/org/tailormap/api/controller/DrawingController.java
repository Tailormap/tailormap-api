/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.annotation.AppRestController;
import org.tailormap.api.drawing.DrawingService;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.viewer.model.Drawing;

/**
 * Controller for drawing operations. Note that the following endpoints are secured/require authentication:
 *
 * <ul>
 *   <li>PUT /drawing (@see DrawingController#createOrUpdateDrawing)
 *   <li>DELETE /drawing/{drawingId} (@see DrawingController#deleteDrawing)
 * </ul>
 *
 * The following endpoints do not require authentication (but may return different results based on the user's role):
 *
 * <ul>
 *   <li>GET /drawing/list (@see DrawingController#listDrawings)
 *   <li>GET /drawing/{drawingId} (@see DrawingController#getDrawing)
 * </ul>
 */
@AppRestController
@Validated
public class DrawingController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final DrawingService drawingService;

  public DrawingController(DrawingService drawingService) {
    this.drawingService = drawingService;
  }

  /**
   * Create or update a drawing. Requires authentication.
   *
   * @param drawing the drawing to create or update
   * @param application the application that this drawing is created or updated in (used to determine the SRID)
   * @return the created or updated drawing
   */
  @PutMapping(
      path = "${tailormap-api.base-path}/{viewerKind}/{viewerName}/drawing",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Timed(value = "create_or_update_drawing", description = "time spent to create or update a drawing")
  @Counted(value = "create_or_update_drawing", description = "number of created or updated drawings")
  @Valid public ResponseEntity<Serializable> createOrUpdateDrawing(
      @NonNull @RequestBody Drawing drawing, @ModelAttribute Application application)
      throws JsonProcessingException {
    logger.trace("create or update drawing {}", drawing);

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    drawing = drawing.srid(getApplicationSrid(application));

    HttpStatus httpStatus;
    if (drawing.getId() == null) {
      drawing = drawingService.createDrawing(drawing, authentication);
      httpStatus = HttpStatus.CREATED;
    } else {
      drawing = drawingService.updateDrawing(drawing, authentication);
      httpStatus = HttpStatus.OK;
    }
    return ResponseEntity.status(httpStatus).body(drawing);
  }

  /**
   * Get a drawing by id. Does not require authentication.
   *
   * @param drawingId the id of the drawing to retrieve
   * @param application the application that this drawing is created or updated in (used to determine the SRID)
   * @return the drawing, if found and accessible
   * @throws ResponseStatusException if the drawing is not found or not accessible
   * @see DrawingService#getDrawing(UUID, Authentication, Boolean, Integer)
   */
  @GetMapping(
      path = "${tailormap-api.base-path}/{viewerKind}/{viewerName}/drawing/{drawingId}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Counted(value = "get_drawing", description = "number of drawings retrieved")
  @Timed(value = "get_drawing", description = "time spent to retrieve a drawing")
  @Valid public ResponseEntity<Serializable> getDrawing(
      @PathVariable UUID drawingId, @ModelAttribute Application application) throws ResponseStatusException {
    logger.trace("get drawing {}", drawingId);

    if (drawingId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Drawing id is required");
    }

    final Drawing drawing = drawingService
        .getDrawing(
            drawingId,
            SecurityContextHolder.getContext().getAuthentication(),
            true,
            getApplicationSrid(application))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Drawing not found"));

    return ResponseEntity.status(HttpStatus.OK).body(drawing);
  }

  /**
   * List drawings. Does not require authentication.
   *
   * @return a, possibly empty, set of drawings
   * @see DrawingService#getDrawingsForUser(Authentication)
   */
  @GetMapping(path = "${tailormap-api.base-path}/drawing/list", produces = MediaType.APPLICATION_JSON_VALUE)
  @Counted(value = "list_drawings", description = "number of drawings listed")
  @Timed(value = "list_drawings", description = "time spent to list drawings")
  public Set<Drawing> listDrawings() {
    return drawingService.getDrawingsForUser(
        SecurityContextHolder.getContext().getAuthentication());
  }

  /**
   * Delete a drawing. Requires authentication.
   *
   * @param drawingId the id of the drawing to delete
   * @return a response entity with status NO_CONTENT
   * @see DrawingService#deleteDrawing(UUID, Authentication)
   */
  @DeleteMapping(path = "${tailormap-api.base-path}/drawing/{drawingId}")
  @Timed(value = "delete_drawing", description = "time spent to delete a drawing")
  @Counted(value = "delete_drawing", description = "number of drawings deleted")
  public ResponseEntity<Serializable> deleteDrawing(@PathVariable UUID drawingId) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    if (drawingId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Drawing id is required");
    }

    drawingService.deleteDrawing(
        drawingId, SecurityContextHolder.getContext().getAuthentication());

    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  private Integer getApplicationSrid(Application application) {
    return Integer.valueOf(
        application.getCrs().substring(application.getCrs().indexOf(":") + 1));
  }
}
