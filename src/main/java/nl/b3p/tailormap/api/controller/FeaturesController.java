/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import io.swagger.v3.oas.annotations.Parameter;

import nl.b3p.tailormap.api.exception.BadRequestException;
import nl.b3p.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import nl.b3p.tailormap.api.geotools.processing.GeometryProcessor;
import nl.b3p.tailormap.api.model.ColumnMetadata;
import nl.b3p.tailormap.api.model.ErrorResponse;
import nl.b3p.tailormap.api.model.Feature;
import nl.b3p.tailormap.api.model.FeaturesResponse;
import nl.b3p.tailormap.api.model.RedirectResponse;
import nl.b3p.tailormap.api.repository.ApplicationLayerRepository;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.security.AuthUtil;
import nl.b3p.tailormap.api.util.Constants;
import nl.tailormap.viewer.config.app.Application;
import nl.tailormap.viewer.config.app.ApplicationLayer;
import nl.tailormap.viewer.config.app.ConfiguredAttribute;
import nl.tailormap.viewer.config.services.AttributeDescriptor;
import nl.tailormap.viewer.config.services.GeoService;
import nl.tailormap.viewer.config.services.Layer;
import nl.tailormap.viewer.config.services.SimpleFeatureType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.data.Query;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.util.GeometricShapeFactory;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceContext;
import javax.validation.constraints.NotNull;

@RestController
@Validated
@RequestMapping(
        path = "/{appId}/features/{appLayerId}",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class FeaturesController implements Constants {
    private final Log logger = LogFactory.getLog(getClass());
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private ApplicationLayerRepository applicationLayerRepository;
    @PersistenceContext private EntityManager entityManager;
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
                "Requested an application or appLayer that does not exist. Message: "
                        + exception.getMessage());
        return new ErrorResponse()
                .message("Requested an application or appLayer that does not exist")
                .code(400);
    }

    /**
     * Handle any {@code BadRequestException} that this controller might throw while getting the
     * application.
     *
     * @param exception the exception
     * @return an error response
     */
    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(
            value =
                    HttpStatus
                            .BAD_REQUEST /*,reason = "Bad Request" -- adding 'reason' will drop the body */)
    @ResponseBody
    public ErrorResponse handleBadRequestException(BadRequestException exception) {
        return new ErrorResponse().message("Bad Request. " + exception.getMessage()).code(400);
    }

    /**
     * Retrieve features that fulfill the requested conditions (parameters).
     *
     * @return a (possibly empty) list of features
     * @param appId the application
     * @param appLayerId the application layer id
     * @param x x-coordinate
     * @param y y-coordinate
     * @param distance buffer distance for radius around selection point(x,y)
     * @param __fid id of feature to get
     * @param simplify set to {@code true} to simplify geometry, defaults to {@code false}
     * @param filter CQL? filter to apply
     * @throws BadRequestException when invalid parameters are passed
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Serializable> getFeatures(
            @Parameter(name = "appId", description = "application id", required = true)
                    @PathVariable("appId")
                    Long appId,
            @Parameter(name = "appLayerId", description = "application layer id", required = true)
                    @PathVariable("appLayerId")
                    Long appLayerId,
            @RequestParam(required = false) Double x,
            @RequestParam(required = false) Double y,
            @RequestParam(defaultValue = "4") Double distance,
            @RequestParam(required = false) String __fid,
            @RequestParam(defaultValue = "false") Boolean simplify,
            @RequestParam(required = false) String filter)
            throws BadRequestException {

        // this could throw EntityNotFound, which is handled by #handleEntityNotFoundException
        // and in a normal flow this should not happen
        // as appId is (should be) validated by calling the /app/ endpoint
        Application application = applicationRepository.getById(appId);
        if (application.isAuthenticatedRequired() && !AuthUtil.isAuthenticatedUser()) {
            // login required, send RedirectResponse
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new RedirectResponse());
        } else {
            ApplicationLayer appLayer = applicationLayerRepository.getById(appLayerId);
            FeaturesResponse featuresResponse = new FeaturesResponse();

            if (null != filter) {
                throw new BadRequestException("filter is not currently supported");
            }
            if (null != __fid) {
                throw new BadRequestException("__fid is not currently supported");
            }

            if (null != x && null != y) {
                featuresResponse = getFeaturesByXY(appLayer, x, y, distance, simplify);
            } else {
                // TODO other implementations
                throw new BadRequestException(
                        "Only x/y/distance/simplify parameters are supported");
            }

            return ResponseEntity.status(HttpStatus.OK).body(featuresResponse);
        }
    }

    @NotNull
    private FeaturesResponse getFeaturesByXY(
            @NotNull ApplicationLayer appLayer,
            @NotNull Double x,
            @NotNull Double y,
            @NotNull Double distance,
            @NotNull Boolean simplifyGeometry)
            throws BadRequestException {
        // validate buffer
        if (null != distance && 0 > distance) {
            throw new BadRequestException("Buffer distance must be greater than 0");
        }

        FeaturesResponse featuresResponse = new FeaturesResponse();
        // find attribute source of layer
        GeoService geoService = appLayer.getService();
        Layer layer = geoService.getLayer(appLayer.getLayerName(), entityManager);
        SimpleFeatureType sft = layer.getFeatureType();
        if (null == sft) {
            return featuresResponse;
        }
        List<ConfiguredAttribute> configuredAttributes = appLayer.getAttributes(sft);
        configuredAttributes =
                configuredAttributes.stream()
                        .filter(ConfiguredAttribute::isVisible)
                        .collect(Collectors.toList());

        try {
            SimpleFeatureSource fs = FeatureSourceFactoryHelper.openGeoToolsFeatureSource(sft);
            Query q = new Query(fs.getName().toString());
            String geomAttribute = fs.getSchema().getGeometryDescriptor().getLocalName();
            FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

            GeometricShapeFactory shapeFact = new GeometricShapeFactory();
            shapeFact.setNumPoints(32);
            shapeFact.setCentre(new Coordinate(x, y));
            shapeFact.setSize(distance * 2d);
            Polygon p = shapeFact.createCircle();
            Filter spatialFilter = ff.intersects(ff.property(geomAttribute), ff.literal(p));

            // TODO flamingo does some fancy stuff to combine with existing filters using
            //      TailormapCQL and some filter visitors

            q.setPropertyNames(
                    configuredAttributes.stream()
                            .map(ConfiguredAttribute::getAttributeName)
                            .collect(Collectors.toList()));
            q.setFilter(spatialFilter);
            q.setMaxFeatures(DEFAULT_MAX_FEATURES);

            boolean addFields = false;
            // send request to attribute source
            try (SimpleFeatureIterator feats = fs.getFeatures(q).features()) {
                while (feats.hasNext()) {
                    addFields = true;
                    // reformat found features to list of Feature, filtering on configuredAttributes
                    SimpleFeature feature = feats.next();
                    Feature newFeat = new Feature().fid(feature.getIdentifier().getID());
                    configuredAttributes.forEach(
                            configuredAttribute -> {
                                if (configuredAttribute
                                        .getAttributeName()
                                        .equals(sft.getGeometryAttribute())) {
                                    newFeat.putAttributesItem(
                                            configuredAttribute.getAttributeName(),
                                            GeometryProcessor.processGeometry(
                                                    feature.getAttribute(
                                                            configuredAttribute.getAttributeName()),
                                                    simplifyGeometry));
                                } else {
                                    newFeat.putAttributesItem(
                                            configuredAttribute.getAttributeName(),
                                            feature.getAttribute(
                                                    configuredAttribute.getAttributeName()));
                                }
                            });
                    featuresResponse.addFeaturesItem(newFeat);
                }
            }
            if (addFields) {
                // get attributes from feature type
                configuredAttributes.forEach(
                        configuredAttribute -> {
                            AttributeDescriptor attributeDescriptor =
                                    configuredAttribute
                                            .getFeatureType()
                                            .getAttribute(configuredAttribute.getAttributeName());
                            featuresResponse.addColumnMetadataItem(
                                    new ColumnMetadata()
                                            .key(attributeDescriptor.getName())
                                            .type(
                                                    ColumnMetadata.TypeEnum.fromValue(
                                                            attributeDescriptor.getType()))
                                            .alias(attributeDescriptor.getAlias()));
                        });
            }
        } catch (IOException e) {
            logger.error("Could not retrieve attribute data.", e);
        }
        return featuresResponse;
    }
}
