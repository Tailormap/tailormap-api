/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import nl.b3p.tailormap.api.model.AppLayer;
import nl.b3p.tailormap.api.model.CoordinateReferenceSystem;
import nl.b3p.tailormap.api.model.ErrorResponse;
import nl.b3p.tailormap.api.model.RedirectResponse;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.security.AuthUtil;
import nl.tailormap.viewer.config.app.Application;
import nl.tailormap.viewer.config.app.ApplicationLayer;
import nl.tailormap.viewer.config.app.StartLayer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityNotFoundException;
import javax.validation.constraints.NotNull;

@RestController
@Validated
@RequestMapping(path = "/app/{appId}/layers", produces = MediaType.APPLICATION_JSON_VALUE)
public class LayersController {
    private final Log logger = LogFactory.getLog(getClass());
    @Autowired private ApplicationRepository applicationRepository;

    /**
     * Handle any {@code EntityNotFoundException} that this controller might throw while getting the
     * application.
     *
     * @param exception the exception
     * @return an error response
     */
    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(
            value =
                    HttpStatus
                            .BAD_REQUEST /*,reason = "Bad Request" -- adding 'reason' will drop the body */)
    @ResponseBody
    public ErrorResponse handleEntityNotFoundException(EntityNotFoundException exception) {
        logger.warn(
                "Requested an application that does not exist. Message: " + exception.getMessage());
        return new ErrorResponse()
                .message("Requested an application that does not exist")
                .code(400);
    }

    /**
     * GET /layers/{appId}.
     *
     * @param appId application id (required)
     * @return OK (status code 200) with list of {@code AppLayer}
     */
    @Operation(
            summary = "",
            tags = {},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "OK",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = AppLayer.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Bad Request",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = RedirectResponse.class)))
            })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(
            @Parameter(name = "appId", description = "application id", required = true)
                    @PathVariable("appId")
                    Long appId) {
        logger.trace("Requesting 'layers' for application id: " + appId);

        // this could throw EntityNotFound, which is handled by #handleEntityNotFoundException
        // and in a normal flow this should not happen
        // as appId is (should be) validated by calling the /app/ endpoint
        Application application = applicationRepository.getById(appId);
        if (application.isAuthenticatedRequired() && !AuthUtil.isAuthenticatedUser()) {
            // login required, send RedirectResponse
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new RedirectResponse());
        } else {
            List<AppLayer> appLayers = new ArrayList<>();
            findApplayers(application, appLayers);
            return ResponseEntity.status(HttpStatus.OK).body(appLayers);
        }
    }

    /**
     * find any applayers for this application and add them to the list.
     *
     * @param application the application
     * @param list the list that will hold the app layers
     */
    private void findApplayers(@NotNull Application application, List<AppLayer> list) {
        ApplicationLayer apll;
        AppLayer a;
        // TODO implement properly
        // find all
        List<StartLayer> startLayers = application.getStartLayers();
        for (StartLayer startLayer : startLayers) {
            apll = startLayer.getApplicationLayer();
            // create API object for each
            a =
                    new AppLayer()
                            .id(apll.getId())
                            .crs(new CoordinateReferenceSystem())
                            .url(apll.getService().getUrl())
                            .isBaseLayer(false)
                            .displayName(apll.getLayerName())
                            .serviceId(apll.getService().getId())
                            .visible(true);
            // add each to list
            list.add(a);
        }
    }
}
