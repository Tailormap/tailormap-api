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
import nl.b3p.tailormap.api.model.Bounds;
import nl.b3p.tailormap.api.model.CoordinateReferenceSystem;
import nl.b3p.tailormap.api.model.ErrorResponse;
import nl.b3p.tailormap.api.model.MapResponse;
import nl.b3p.tailormap.api.model.Service;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.tailormap.viewer.config.app.Application;
import nl.tailormap.viewer.config.app.StartLayer;
import nl.tailormap.viewer.config.services.GeoService;
import nl.tailormap.viewer.config.services.TileMatrixSet;
import nl.tailormap.viewer.config.services.TileService;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import javax.persistence.EntityNotFoundException;
import javax.validation.constraints.NotNull;

@RestController
@Validated
@RequestMapping(path = "/map/{appid}", produces = MediaType.APPLICATION_JSON_VALUE)
public class MapController {
    private final Log logger = LogFactory.getLog(getClass());
    @Autowired private ApplicationRepository applicationRepository;

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
                                        schema = @Schema(implementation = MapResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Bad Request",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ErrorResponse.class)))
            })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public MapResponse get(
            @Parameter(name = "appid", description = "application id", required = true)
                    @PathVariable("appid")
                    Long appid) {
        logger.trace("Requesting 'map' for application id: " + appid);

        MapResponse mapResponse = new MapResponse();

        // this could throw EntityNotFound, which is handles by handleEntityNotFoundException
        // and in a normal flow this should not happen
        // as appid is (should be) validated by calling the /app/ endpoint
        Application application = applicationRepository.getById(appid);

        getApplicationParams(application, mapResponse);
        getLayers(application, mapResponse);

        return mapResponse;
    }

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

    private void getApplicationParams(@NotNull Application a, @NotNull MapResponse mapResponse) {
        final String pCode = a.getProjectionCode();
        //        final String pCode =
        //                "EPSG:28992[+proj=sterea +lat_0=52.15616055555555 +lon_0=5.38763888888889
        // +k=0.9999079 +x_0=155000 +y_0=463000 +ellps=bessel
        // +towgs84=565.237,50.0087,465.658,-0.406857,0.350733,-1.87035,4.0812 +units=m +no_defs]";
        CoordinateReferenceSystem c = new CoordinateReferenceSystem();
        if (null != pCode) {
            c.code(pCode.substring(0, pCode.indexOf('[')))
                    .definition(pCode.substring(pCode.indexOf('[') + 1, pCode.lastIndexOf(']')));
        }
        Bounds maxExtent = new Bounds();
        if (null != a.getMaxExtent()) {
            maxExtent
                    .minx(a.getMaxExtent().getMinx())
                    .miny(a.getMaxExtent().getMiny())
                    .maxx(a.getMaxExtent().getMaxx())
                    .maxy(a.getMaxExtent().getMaxy())
                    .crs(a.getMaxExtent().getCrs().getName());
        }
        Bounds initialExtent = new Bounds();
        if (null != a.getStartExtent()) {
            initialExtent
                    .minx(a.getStartExtent().getMinx())
                    .miny(a.getStartExtent().getMiny())
                    .maxx(a.getStartExtent().getMaxx())
                    .maxy(a.getStartExtent().getMaxy())
                    .crs(a.getStartExtent().getCrs().getName());
        }

        mapResponse.crs(c).maxExtent(maxExtent).initialExtent(initialExtent);
    }

    private void getLayers(@NotNull Application a, @NotNull MapResponse mapResponse) {
        List<StartLayer> startLayers = a.getStartLayers();
        if (null != a.getStartLayers()) {
            for (StartLayer l : startLayers) {
                AppLayer appLayer =
                        new AppLayer()
                                .id(l.getApplicationLayer().getId())
                                .displayName(l.getApplicationLayer().getLayerName())
                                .url(l.getApplicationLayer().getService().getUrl())
                                .serviceId(l.getApplicationLayer().getService().getId())
                                // TODO fixup hardcoded data
                                .crs(new CoordinateReferenceSystem())
                                .visible(true)
                                .isBaseLayer(true);

                GeoService geoService = l.getApplicationLayer().getService();
                Service s =
                        new Service()
                                .url(geoService.getUrl())
                                .id(geoService.getId())
                                .name(geoService.getName())
                                .protocol(Service.ProtocolEnum.fromValue(geoService.getProtocol()));
                if (geoService.getProtocol().equalsIgnoreCase(TileService.PROTOCOL)) {
                    s.tilingProtocol(
                            Service.TilingProtocolEnum.fromValue(
                                    ((TileService) geoService).getTilingProtocol()));

                    // TODO
                    // find first matrix set that sort of matches on the numeric part of the CRS
                    // code
                    TileMatrixSet tileMatrixSet =
                            ((TileService) geoService)
                                    .getMatrixSets().stream()
                                            .filter(
                                                    tms ->
                                                            tms.getCrs()
                                                                    .contains(
                                                                            appLayer.getCrs()
                                                                                    .getCode()
                                                                                    .substring(5)))
                                            .findFirst()
                                            .orElse(null);
                    if (null != tileMatrixSet) {
                        // you cannot add the JSONObject or the tileMatrixSet; that just does not
                        // work,
                        // but you can add a Map
                        s.matrixSet(tileMatrixSet.toJSONObject().toMap());
                    }
                }
                if (appLayer.getIsBaseLayer()) {
                    mapResponse.addBaseLayersItem(appLayer);
                }
                mapResponse.addServicesItem(s);
            }
        }
    }
}
