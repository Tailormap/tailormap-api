/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.admin.model.CadastralMunicipality;
import org.tailormap.api.pdok.CadastralMunicipalityService;
import org.tailormap.api.viewer.model.ErrorResponse;

@RestController
public class CadastralMunicipalityAdminController {
  private final CadastralMunicipalityService cadastralMunicipalityService;

  public CadastralMunicipalityAdminController(CadastralMunicipalityService cadastralMunicipalityService) {
    this.cadastralMunicipalityService = cadastralMunicipalityService;
  }

  @ExceptionHandler({ResponseStatusException.class})
  public ResponseEntity<?> handleException(ResponseStatusException ex) {
    // wrap the exception in a proper json response
    return ResponseEntity.status(ex.getStatusCode())
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ErrorResponse()
            .message(
                ex.getReason() != null
                    ? ex.getReason()
                    : ex.getBody().getTitle())
            .code(ex.getStatusCode().value()));
  }

  @Operation(
      summary = "List the kadastrale gemeenten (cadastral municipalities)",
      description = "List all kadastrale gemeente (AKR) codes with their names that have parcels in the PDOK"
          + " Locatieserver, sorted by code. The list is built from PDOK on the first request (which can take"
          + " several seconds) and cached and refreshed periodically.")
  @GetMapping(
      path = "${tailormap-api.admin.base-path}/pdok/cadastral-municipalities",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiResponse(
      responseCode = "200",
      description = "The list of cadastral municipalities",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              array = @ArraySchema(schema = @Schema(implementation = CadastralMunicipality.class))))
  @ApiResponse(
      responseCode = "404",
      description = "Loading the list of cadastral municipalities is disabled",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "503",
      description = "The list of cadastral municipalities could not be loaded from PDOK",
      content =
          @Content(
              mediaType = MediaType.APPLICATION_JSON_VALUE,
              schema = @Schema(implementation = ErrorResponse.class)))
  public ResponseEntity<List<CadastralMunicipality>> getCadastralMunicipalities() {
    if (!cadastralMunicipalityService.isEnabled()) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Loading the list of cadastral municipalities is disabled");
    }
    try {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
          .body(cadastralMunicipalityService
              .getCadastralMunicipalities()
              .municipalities());
    } catch (IOException e) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "The list of cadastral municipalities could not be loaded from PDOK",
          e);
    }
  }
}
