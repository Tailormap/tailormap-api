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

import nl.b3p.tailormap.api.geotools.referencing.ReferencingHelper;
import nl.b3p.tailormap.api.model.AppLayer;
import nl.b3p.tailormap.api.model.Bounds;
import nl.b3p.tailormap.api.model.CoordinateReferenceSystem;
import nl.b3p.tailormap.api.model.ErrorResponse;
import nl.b3p.tailormap.api.model.LayerTreeNode;
import nl.b3p.tailormap.api.model.MapResponse;
import nl.b3p.tailormap.api.model.RedirectResponse;
import nl.b3p.tailormap.api.model.Service;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.repository.LevelRepository;
import nl.b3p.tailormap.api.security.AuthUtil;
import nl.b3p.tailormap.api.util.ParseUtil;
import nl.tailormap.viewer.config.ClobElement;
import nl.tailormap.viewer.config.app.Application;
import nl.tailormap.viewer.config.app.ApplicationLayer;
import nl.tailormap.viewer.config.app.Level;
import nl.tailormap.viewer.config.app.StartLayer;
import nl.tailormap.viewer.config.app.StartLevel;
import nl.tailormap.viewer.config.services.GeoService;
import nl.tailormap.viewer.config.services.Layer;
import nl.tailormap.viewer.config.services.TileService;

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

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityNotFoundException;
import javax.validation.constraints.NotNull;

@RestController
@Validated
@RequestMapping(path = "/app/{appId}/map", produces = MediaType.APPLICATION_JSON_VALUE)
public class MapController {
    private final Log logger = LogFactory.getLog(getClass());
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private LevelRepository levelRepository;

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
    @ResponseBody
    public ResponseEntity<Serializable> get(
            @Parameter(name = "appId", description = "application id", required = true)
                    @PathVariable("appId")
                    Long appId) {
        logger.trace("Requesting 'map' for application id: " + appId);

        // this could throw EntityNotFound, which is handles by handleEntityNotFoundException
        // and in a normal flow this should not happen
        // as appId is (should be) validated by calling the /app/ endpoint
        Application application = applicationRepository.getReferenceById(appId);
        if (application.isAuthenticatedRequired() && !AuthUtil.isAuthenticatedUser()) {
            // login required, send RedirectResponse
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new RedirectResponse());
        } else {
            MapResponse mapResponse = new MapResponse();
            getApplicationParams(application, mapResponse);
            getLayers(application, mapResponse);

            return ResponseEntity.status(HttpStatus.OK).body(mapResponse);
        }
    }

    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(
            value =
                    HttpStatus
                            .NOT_FOUND /*,reason = "Not Found" -- adding 'reason' will drop the body */)
    @ResponseBody
    public ErrorResponse handleEntityNotFoundException(EntityNotFoundException exception) {
        logger.warn(
                "Requested an application that does not exist. Message: " + exception.getMessage());
        return new ErrorResponse()
                .message("Requested an application that does not exist")
                .code(HttpStatus.NOT_FOUND.value());
    }

    private void getApplicationParams(@NotNull Application a, @NotNull MapResponse mapResponse) {
        final String pCode = a.getProjectionCode();

        CoordinateReferenceSystem c = new CoordinateReferenceSystem();
        if (null != pCode) {
            c.code(ParseUtil.parseEpsgCode(pCode)).definition(ParseUtil.parseProjDefintion(pCode));
        }
        Bounds maxExtent = new Bounds();
        if (null != a.getMaxExtent()) {
            maxExtent
                    .minx(a.getMaxExtent().getMinx())
                    .miny(a.getMaxExtent().getMiny())
                    .maxx(a.getMaxExtent().getMaxx())
                    .maxy(a.getMaxExtent().getMaxy())
                    .crs(a.getMaxExtent().getCrs().getName());
        } else {
            maxExtent = ReferencingHelper.crsBoundsExtractor(c.getCode());
        }
        Bounds initialExtent = new Bounds();
        if (null != a.getStartExtent()) {
            initialExtent
                    .minx(a.getStartExtent().getMinx())
                    .miny(a.getStartExtent().getMiny())
                    .maxx(a.getStartExtent().getMaxx())
                    .maxy(a.getStartExtent().getMaxy())
                    .crs(a.getStartExtent().getCrs().getName());
        } else {
            initialExtent = maxExtent;
        }

        mapResponse.crs(c).maxExtent(maxExtent).initialExtent(initialExtent);
    }

    private void getLayers(@NotNull Application a, @NotNull MapResponse mapResponse) {
        LayerTreeNode rootNode = new LayerTreeNode().id("root").root(true).name("Foreground");
        mapResponse.addLayerTreeNodesItem(rootNode);

        LayerTreeNode rootBackgroundNode =
                new LayerTreeNode().id("rootbg").root(true).name("Background");
        mapResponse.addBaseLayerTreeNodesItem(rootBackgroundNode);

        levelRepository.findByLevelTree(a.getRoot().getId());
        List<StartLayer> startLayers = a.getStartLayers();
        List<StartLevel> startLevels = a.getStartLevels();

        // Build a map of layer id -> StartLayer.
        Map<Long, StartLayer> layerMap = new HashMap<>(startLayers.size());
        for (StartLayer startLayer : startLayers) {
            if (startLayer.isRemoved()) {
                continue;
            }

            layerMap.put(startLayer.getApplicationLayer().getId(), startLayer);
        }

        // Remove any startLevels that aren't assigned in the startkaartbeeld
        startLevels.removeIf((StartLevel t) -> t.isRemoved() || t.getSelectedIndex() == null);
        startLevels.sort(Comparator.comparingLong(StartLevel::getSelectedIndex));

        Map<Long, LayerTreeNode> treeNodeMap = new HashMap<>();
        Deque<Level> levelQueue = new ArrayDeque<>();
        List<StartLayer> visibleStartLayers = new ArrayList<>();

        for (StartLevel l : startLevels) {
            // Check if this level is a child of a background level. In the API background levels
            // are returned in a separate tree.
            // Only children of the Level with isBackground() set to true can be a StartLevel, so we
            // need to check all parents only (not the Level of the StartLevel itself).
            boolean isBackground = false;
            Level parentLevel = l.getLevel();
            while (parentLevel != null && !isBackground) {
                isBackground = parentLevel.isBackground();
                parentLevel = parentLevel.getParent();
            }

            Level startLevel = l.getLevel();
            List<LayerTreeNode> treeNodeList;
            LayerTreeNode chosenRoot;
            if (isBackground) {
                treeNodeList = mapResponse.getBaseLayerTreeNodes();
                chosenRoot = rootBackgroundNode;
            } else {
                treeNodeList = mapResponse.getLayerTreeNodes();
                chosenRoot = rootNode;
            }

            levelQueue.add(startLevel);
            while (!levelQueue.isEmpty()) {
                Level level = levelQueue.pop();
                if (treeNodeMap.containsKey(level.getId())) {
                    continue;
                }

                // Use a prefix to make the LayerTreeNode ids in the tree containing both Level and
                // ApplicationLayer nodes unique

                LayerTreeNode childNode =
                        new LayerTreeNode()
                                .id(String.format("lvl_%d", level.getId()))
                                .name(level.getName())
                                .root(false)
                                .childrenIds(new ArrayList<>());

                treeNodeList.add(childNode);
                treeNodeMap.put(level.getId(), childNode);

                LayerTreeNode parentNode;
                if (level == startLevel) {
                    parentNode = chosenRoot;
                } else {
                    parentNode = treeNodeMap.get(level.getParent().getId());
                }
                parentNode.addChildrenIdsItem(childNode.getId());

                levelQueue.addAll(level.getChildren());
                for (ApplicationLayer layer : level.getLayers()) {
                    StartLayer startLayer = layerMap.get(layer.getId());
                    if (startLayer == null) {
                        continue;
                    }

                    LayerTreeNode layerNode =
                            new LayerTreeNode()
                                    .id(String.format("lyr_%d", layer.getId()))
                                    .name(layer.getLayerName())
                                    .appLayerId((int) (long) layer.getId())
                                    .root(false)
                                    .childrenIds(new ArrayList<>());

                    treeNodeList.add(layerNode);
                    childNode.addChildrenIdsItem(layerNode.getId());
                    visibleStartLayers.add(startLayer);
                }
            }
        }

        // Only add AppLayers visible in the LayerTreeNode graph to the response
        for (StartLayer l : visibleStartLayers) {

            Layer serviceLayer =
                    findLayer(
                            l.getApplicationLayer().getService().getTopLayer(),
                            l.getApplicationLayer().getLayerName());

            AppLayer.HiDpiModeEnum hiDpiMode = null;
            String hiDpiSubstituteLayer = null;

            if (serviceLayer != null) {
                ClobElement ce = serviceLayer.getDetails().get("hidpi.mode");
                if (ce != null) {
                    try {
                        hiDpiMode = AppLayer.HiDpiModeEnum.fromValue(ce.getValue());
                    } catch (IllegalArgumentException e) {
                        logger.warn(
                                String.format(
                                        "App #%s (%s): invalid hidpi.mode enum value for app layer #%s, service layer #%s (%s)",
                                        a.getId(),
                                        a.getNameWithVersion(),
                                        l.getId(),
                                        serviceLayer.getId(),
                                        serviceLayer.getName()));
                    }
                }
                ce = serviceLayer.getDetails().get("hidpi.substitute_layer");
                if (ce != null) {
                    hiDpiSubstituteLayer = ce.getValue();
                }
            }
            AppLayer appLayer =
                    new AppLayer()
                            .id(l.getApplicationLayer().getId())
                            .layerName(l.getApplicationLayer().getLayerName())
                            // TODO: see ApplicationLayer.getDisplayName(), but this method requires
                            // an EntityManager
                            .title(l.getApplicationLayer().getLayerName())
                            .serviceId(l.getApplicationLayer().getService().getId())
                            .hiDpiMode(hiDpiMode)
                            .hiDpiSubstituteLayer(hiDpiSubstituteLayer)
                            .visible(l.isChecked())
                            .hasAttributes(!l.getApplicationLayer().getAttributes().isEmpty());

            mapResponse.addAppLayersItem(appLayer);

            GeoService geoService = l.getApplicationLayer().getService();

            // Use this default if saved before the form default was added in admin
            Service.HiDpiModeEnum serviceHiDpiMode = Service.HiDpiModeEnum.AUTO;
            ClobElement ce = geoService.getDetails().get("hidpi.mode");
            if (ce != null) {
                try {
                    serviceHiDpiMode = Service.HiDpiModeEnum.fromValue(ce.getValue());
                } catch (IllegalArgumentException e) {
                    logger.warn(
                            String.format(
                                    "App #%s (%s): invalid hidpi.mode enum value for service #%s (%s)",
                                    a.getId(),
                                    a.getNameWithVersion(),
                                    geoService.getId(),
                                    geoService.getName()));
                }
            }
            Integer tilingGutter = null;
            ce = geoService.getDetails().get("tiling.gutter");
            if (ce != null) {
                try {
                    tilingGutter = Integer.parseInt(ce.getValue());
                } catch (NumberFormatException ignored) {
                    // ignored
                }
            }

            Service s =
                    new Service()
                            .url(geoService.getUrl())
                            .id(geoService.getId())
                            .name(geoService.getName())
                            .protocol(Service.ProtocolEnum.fromValue(geoService.getProtocol()))
                            .hiDpiMode(serviceHiDpiMode)
                            .tilingDisabled(
                                    "true"
                                            .equals(
                                                    geoService
                                                            .getDetails()
                                                            .getOrDefault(
                                                                    "tiling.disable",
                                                                    new ClobElement("false)"))
                                                            .getValue()))
                            .tilingGutter(tilingGutter)
                            .capabilities(geoService.getCapabilitiesDoc());
            if (geoService.getProtocol().equalsIgnoreCase(TileService.PROTOCOL)) {
                s.tilingProtocol(
                        Service.TilingProtocolEnum.fromValue(
                                ((TileService) geoService).getTilingProtocol()));
            }
            mapResponse.addServicesItem(s);
        }
    }

    /**
     * Recursive and naive way to search for a layer by name in the layer tree of a service. Needs
     * to be replaced by an algorithm that does not cause a lot of queries.
     *
     * @param l Layer to start searching for including children
     * @param name The layer name to search for
     * @return null if not found in this subtree or a Layer if found
     */
    private static Layer findLayer(Layer l, String name) {
        if (name.equals(l.getName())) {
            return l;
        }
        for (Layer child : l.getChildren()) {
            Layer childResult = findLayer(child, name);
            if (childResult != null) {
                return childResult;
            }
        }
        return null;
    }
}
