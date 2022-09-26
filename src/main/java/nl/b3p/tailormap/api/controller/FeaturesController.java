/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import io.micrometer.core.annotation.Timed;
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
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultProjectedCRS;
import org.geotools.util.factory.GeoTools;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.util.GeometricShapeFactory;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.sort.SortOrder;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
        path = "/app/{appId}/layer/{appLayerId}/features",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class FeaturesController implements Constants {
    @Value("${tailormap-api.pageSize:100}")
    private int pageSize;

    @Value("${tailormap-api.features.wfs_count_exact:false}")
    private boolean exactWfsCounts;

    @Value("${tailormap-api.features.skip_geometry_output:true}")
    private boolean skipGeometryOutput;

    private final FilterFactory2 ff =
            CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
    private final Log logger = LogFactory.getLog(getClass());
    private final ApplicationRepository applicationRepository;
    private final ApplicationLayerRepository applicationLayerRepository;
    @PersistenceContext private EntityManager entityManager;

    @Autowired
    public FeaturesController(
            ApplicationRepository applicationRepository,
            ApplicationLayerRepository applicationLayerRepository) {
        this.applicationRepository = applicationRepository;
        this.applicationLayerRepository = applicationLayerRepository;
    }

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
                            .NOT_FOUND /*,reason = "Not Found" -- adding 'reason' will drop the body */)
    @ResponseBody
    public ErrorResponse handleEntityNotFoundException(EntityNotFoundException exception) {
        logger.warn(
                "Requested an application or appLayer that does not exist. Message: "
                        + exception.getMessage());
        return new ErrorResponse()
                .message("Requested an application or appLayer that does not exist")
                .code(HttpStatus.NOT_FOUND.value());
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
     * @param crs CRS for x- and y-coordinate
     * @param distance buffer distance for radius around selection point(x,y)
     * @param __fid id of feature to get
     * @param simplify set to {@code true} to simplify geometry, defaults to {@code false}
     * @param filter CQL? filter to apply
     * @param page Page number to retrieve, starts at 1
     * @param sortBy attribute to sort by
     * @param sortOrder sort order of features, defaults to {@code ASC}
     * @throws BadRequestException when invalid parameters are passed
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed(value = "get_features", description = "time spent to process get features call")
    public ResponseEntity<Serializable> getFeatures(
            @Parameter(name = "appId", description = "application id", required = true)
                    @PathVariable("appId")
                    Long appId,
            @Parameter(name = "appLayerId", description = "application layer id", required = true)
                    @PathVariable("appLayerId")
                    Long appLayerId,
            @RequestParam(required = false) Double x,
            @RequestParam(required = false) Double y,
            @RequestParam(required = false) String crs,
            @RequestParam(defaultValue = "4") Double distance,
            @RequestParam(required = false) String __fid,
            @RequestParam(defaultValue = "false") Boolean simplify,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortOrder)
            throws BadRequestException {

        // this could throw EntityNotFound, which is handled by #handleEntityNotFoundException
        // and in a normal flow this should not happen
        // as appId is (should be) validated by calling the /app/ endpoint
        Application application = applicationRepository.getReferenceById(appId);
        if (application.isAuthenticatedRequired() && !AuthUtil.isAuthenticatedUser()) {
            // login required, send RedirectResponse
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new RedirectResponse());
        } else {
            ApplicationLayer appLayer = applicationLayerRepository.getReferenceById(appLayerId);
            FeaturesResponse featuresResponse;

            if (null != __fid) {
                featuresResponse = getFeatureByFID(appLayer, __fid, crs);
            } else if (null != x && null != y) {
                featuresResponse = getFeaturesByXY(appLayer, x, y, crs, distance, simplify);
            } else if (null != page && page > 0) {
                featuresResponse = getAllFeatures(appLayer, crs, page, filter, sortBy, sortOrder);
            } else {
                // TODO other implementations
                throw new BadRequestException(
                        "Unsupported combination of request parameters, please check the documentation");
            }

            return ResponseEntity.status(HttpStatus.OK).body(featuresResponse);
        }
    }

    @NotNull
    private FeaturesResponse getAllFeatures(
            @NotNull ApplicationLayer appLayer,
            String crs,
            Integer page,
            String filterCQL,
            String sortBy,
            String sortOrder)
            throws BadRequestException {
        FeaturesResponse featuresResponse = new FeaturesResponse().page(page).pageSize(pageSize);

        // find attribute source of layer
        final Layer layer = appLayer.getService().getLayer(appLayer.getLayerName(), entityManager);
        final SimpleFeatureType sft = layer.getFeatureType();
        if (null == sft) {
            return featuresResponse;
        }
        List<ConfiguredAttribute> configuredAttributes = getVisibleAttributes(appLayer, sft);
        try {
            SimpleFeatureSource fs = FeatureSourceFactoryHelper.openGeoToolsFeatureSource(sft);

            List<String> propNames =
                    configuredAttributes.stream()
                            .map(ConfiguredAttribute::getAttributeName)
                            .collect(Collectors.toList());

            // TODO evaluate; do we want geometry in this response or not?
            //  if we do the geometry attribute must not be removed from propNames
            propNames.remove(sft.getGeometryAttribute());

            String sortAttrName;
            // determine sorting attribute, default to first attribute or primary key
            sortAttrName = propNames.get(0);
            if (propNames.contains(sft.getPrimaryKeyAttribute())) {
                // there is a primary key and it is known, use that for sorting
                sortAttrName = sft.getPrimaryKeyAttribute();
                logger.trace("Sorting by primary key");
            } else {
                // there is no primary key we know of
                // pick the first one from sft that is not geometry and is in the list of configured
                // attributes
                // note that propNames does not have the default geometry attribute (see above)
                for (AttributeDescriptor attrDesc : sft.getAttributes()) {
                    if (propNames.contains(attrDesc.getName())) {
                        sortAttrName = attrDesc.getName();
                        break;
                    }
                }
            }

            if (null != sortBy) {
                // validate sortBy attribute is in the list of configured attributes
                // and not a geometry type

                if (propNames.contains(sortBy)
                        && !(sft.getAttribute(sortBy) instanceof GeometryDescriptor)) {
                    sortAttrName = sortBy;
                } else {
                    logger.warn(
                            "Requested sortBy attribute "
                                    + sortBy
                                    + " was not found in configured attributes or is a geometry attribute.");
                }
            }

            SortOrder _sortOrder = SortOrder.ASCENDING;
            if (null != sortOrder
                    && (sortOrder.equalsIgnoreCase("desc") || sortOrder.equalsIgnoreCase("asc"))) {
                _sortOrder = SortOrder.valueOf(sortOrder.toUpperCase());
            }

            // setup query, attributes and filter
            Query q = new Query(fs.getName().toString());
            q.setPropertyNames(propNames);

            // count can be -1 if too costly eg. some WFS
            int featureCount;
            if (null != filterCQL) {
                Filter filter = ECQL.toFilter(filterCQL);
                q.setFilter(filter);
                featureCount = fs.getCount(q);
                // this will execute the query twice, once to get the count and once to get the data
                if (featureCount == -1 && exactWfsCounts) {
                    featureCount = fs.getFeatures(q).size();
                }
            } else {
                featureCount = fs.getCount(Query.ALL);
                // this will execute the query twice, once to get the count and once to get the data
                if (featureCount == -1 && exactWfsCounts) {
                    featureCount = fs.getFeatures(Query.ALL).size();
                }
            }
            featuresResponse.setTotal(featureCount);

            // setup page query
            q.setSortBy(ff.sort(sortAttrName, _sortOrder));
            q.setMaxFeatures(pageSize);
            q.setStartIndex((page - 1) * pageSize);
            logger.debug("Attribute query: " + q);

            executeQueryOnFeatureSourceAndClose(
                    false,
                    featuresResponse,
                    sft,
                    configuredAttributes,
                    fs,
                    q,
                    determineProjectToCRS(crs, fs));
        } catch (IOException e) {
            logger.error("Could not retrieve attribute data.", e);
        } catch (CQLException e) {
            logger.error("Could not parse requested filter.", e);
            throw new BadRequestException("Could not parse requested filter.");
        }

        return featuresResponse;
    }

    @NotNull
    private FeaturesResponse getFeatureByFID(
            @NotNull ApplicationLayer appLayer, @NotNull String fid, String crs) {
        FeaturesResponse featuresResponse = new FeaturesResponse();

        // find attribute source of layer
        final Layer layer = appLayer.getService().getLayer(appLayer.getLayerName(), entityManager);
        final SimpleFeatureType sft = layer.getFeatureType();
        if (null == sft) {
            return featuresResponse;
        }
        List<ConfiguredAttribute> configuredAttributes = getVisibleAttributes(appLayer, sft);
        try {
            SimpleFeatureSource fs = FeatureSourceFactoryHelper.openGeoToolsFeatureSource(sft);

            List<String> propNames =
                    configuredAttributes.stream()
                            .map(ConfiguredAttribute::getAttributeName)
                            .collect(Collectors.toList());
            if (!propNames.contains(sft.getGeometryAttribute())) {
                // add geom attribute for highlighting
                propNames.add(sft.getGeometryAttribute());
            }

            // setup page query
            Query q = new Query(fs.getName().toString());
            q.setFilter(ff.id(ff.featureId(fid)));
            q.setPropertyNames(propNames);
            q.setMaxFeatures(1);
            logger.debug("FID query: " + q);

            executeQueryOnFeatureSourceAndClose(
                    false,
                    featuresResponse,
                    sft,
                    configuredAttributes,
                    fs,
                    q,
                    determineProjectToCRS(crs, fs));
        } catch (IOException e) {
            logger.error("Could not retrieve attribute data.", e);
        }

        return featuresResponse;
    }

    /**
     * @param requestCrs requeste CRS, may be null
     * @param fs the feature source to query
     * @return the CRS to use for the reprojection of query results, {@code null} if no CRS is
     *     requested or when datasource CRS is the same as the requested crs
     */
    private CoordinateReferenceSystem determineProjectToCRS(
            String requestCrs, SimpleFeatureSource fs) {
        CoordinateReferenceSystem projectToCRS = null;
        if (null != requestCrs) {
            // reproject to feature source CRS if different from source CRS
            try {
                // this is the CRS of the "default geometry" attribute
                final CoordinateReferenceSystem dataSourceCRS =
                        fs.getSchema().getCoordinateReferenceSystem();
                if (!((DefaultProjectedCRS) dataSourceCRS)
                        .getIdentifier(null)
                        .toString()
                        .equalsIgnoreCase(requestCrs)) {
                    projectToCRS = CRS.decode(requestCrs);
                }
            } catch (FactoryException e) {
                logger.warn("Unable to transform query geometry to desired CRS", e);
            }
        }
        return projectToCRS;
    }

    @NotNull
    private FeaturesResponse getFeaturesByXY(
            @NotNull ApplicationLayer appLayer,
            @NotNull Double x,
            @NotNull Double y,
            String crs,
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

            GeometricShapeFactory shapeFact = new GeometricShapeFactory();
            shapeFact.setNumPoints(32);
            shapeFact.setCentre(new Coordinate(x, y));
            //noinspection ConstantConditions
            shapeFact.setSize(distance * 2d);
            Geometry p = shapeFact.createCircle();
            logger.debug("created geometry: " + p);

            CoordinateReferenceSystem fromCRS = determineProjectToCRS(crs, fs);
            if (null != fromCRS) {
                // reproject requested x/y-geom to feature source CRS if different from source CRS
                try {
                    // this is the CRS of the "default geometry" attribute
                    final CoordinateReferenceSystem toCRS =
                            fs.getSchema().getCoordinateReferenceSystem();
                    MathTransform transform = CRS.findMathTransform(fromCRS, toCRS, true);
                    p = JTS.transform(p, transform);
                    logger.debug("reprojected geometry to: " + p);
                } catch (FactoryException | TransformException e) {
                    logger.warn(
                            "Unable to transform query geometry to desired CRS, trying with original CRS");
                }
            }
            logger.debug("using geometry: " + p);
            Filter spatialFilter =
                    ff.intersects(ff.property(sft.getGeometryAttribute()), ff.literal(p));

            // TODO flamingo does some fancy stuff to combine with existing filters using
            //      TailormapCQL and some filter visitors

            List<String> propNames =
                    configuredAttributes.stream()
                            .map(ConfiguredAttribute::getAttributeName)
                            .collect(Collectors.toList());
            if (!propNames.contains(sft.getGeometryAttribute())) {
                // add geom attribute for highlighting
                propNames.add(sft.getGeometryAttribute());
            }

            q.setPropertyNames(propNames);
            q.setFilter(spatialFilter);
            q.setMaxFeatures(DEFAULT_MAX_FEATURES);

            executeQueryOnFeatureSourceAndClose(
                    simplifyGeometry, featuresResponse, sft, configuredAttributes, fs, q, fromCRS);
        } catch (IOException e) {
            logger.error("Could not retrieve attribute data.", e);
        }
        return featuresResponse;
    }

    private void executeQueryOnFeatureSourceAndClose(
            boolean simplifyGeometry,
            @NotNull FeaturesResponse featuresResponse,
            @NotNull SimpleFeatureType sft,
            List<ConfiguredAttribute> configuredAttributes,
            @NotNull SimpleFeatureSource fs,
            @NotNull Query q,
            CoordinateReferenceSystem projectToCRS)
            throws IOException {
        boolean addFields = false;

        MathTransform transform = null;
        if (null != projectToCRS) {
            try {
                transform =
                        CRS.findMathTransform(
                                fs.getSchema().getCoordinateReferenceSystem(), projectToCRS, true);
            } catch (FactoryException e) {
                logger.error("Can not transform geometry to desired CRS.", e);
            }
        }

        // send request to attribute source
        try (SimpleFeatureIterator feats = fs.getFeatures(q).features()) {
            while (feats.hasNext()) {
                addFields = true;
                // reformat found features to list of Feature, filtering on configuredAttributes
                SimpleFeature feature = feats.next();
                // processedGeometry can be null
                String processedGeometry =
                        GeometryProcessor.processGeometry(
                                feature.getAttribute(sft.getGeometryAttribute()),
                                simplifyGeometry,
                                transform);
                Feature newFeat =
                        new Feature()
                                .fid(feature.getIdentifier().getID())
                                .geometry(processedGeometry);

                configuredAttributes.forEach(
                        configuredAttribute -> {
                            if (configuredAttribute
                                    .getAttributeName()
                                    .equals(sft.getGeometryAttribute())) {
                                newFeat.putAttributesItem(
                                        configuredAttribute.getAttributeName(), processedGeometry);
                            } else {
                                Object attrValue =
                                        feature.getAttribute(
                                                configuredAttribute.getAttributeName());
                                if (attrValue instanceof Geometry) {
                                    if (skipGeometryOutput) {
                                        attrValue = null;
                                    } else {
                                        attrValue =
                                                GeometryProcessor.geometryToJson(
                                                        (Geometry) attrValue);
                                    }
                                }
                                newFeat.putAttributesItem(
                                        configuredAttribute.getAttributeName(), attrValue);
                            }
                        });
                featuresResponse.addFeaturesItem(newFeat);
            }
        } finally {
            fs.getDataStore().dispose();
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
    }

    private List<ConfiguredAttribute> getVisibleAttributes(
            @NotNull ApplicationLayer appLayer, @NotNull SimpleFeatureType sft) {
        List<ConfiguredAttribute> configuredAttributes = appLayer.getAttributes(sft);
        return configuredAttributes.stream()
                .filter(ConfiguredAttribute::isVisible)
                .collect(Collectors.toList());
    }
}
