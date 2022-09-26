/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Parameter;

import nl.b3p.tailormap.api.exception.BadRequestException;
import nl.b3p.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import nl.b3p.tailormap.api.model.ErrorResponse;
import nl.b3p.tailormap.api.model.RedirectResponse;
import nl.b3p.tailormap.api.model.UniqueValuesResponse;
import nl.b3p.tailormap.api.repository.ApplicationLayerRepository;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.security.AuthUtil;
import nl.tailormap.viewer.config.app.Application;
import nl.tailormap.viewer.config.app.ApplicationLayer;
import nl.tailormap.viewer.config.services.Layer;
import nl.tailormap.viewer.config.services.SimpleFeatureType;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geotools.data.Query;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.text.cql2.CQLException;
import org.geotools.filter.text.ecql.ECQL;
import org.geotools.util.factory.GeoTools;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.expression.Function;
import org.opengis.filter.sort.SortOrder;
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
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.PersistenceContext;

@RestController
@Validated
@RequestMapping(
        path = "/app/{appId}/layer/{appLayerId}/unique/{attributeName}",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class UniqueValuesController {
    @Value("${tailormap-api.unique.use_geotools_unique_function:true}")
    private boolean useGeotoolsUniqueFunction;

    private final FilterFactory2 ff =
            CommonFactoryFinder.getFilterFactory2(GeoTools.getDefaultHints());
    private final Log logger = LogFactory.getLog(getClass());
    private final ApplicationRepository applicationRepository;
    private final ApplicationLayerRepository applicationLayerRepository;
    @PersistenceContext private EntityManager entityManager;

    @Autowired
    public UniqueValuesController(
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
     * Get a list of unique attribute values for a given attribute name.
     *
     * @param appId the application
     * @param appLayerId the application layer id
     * @param attributeName the attribute name
     * @param filter A filter that was already applied to the layer (on a different attribute or
     *     this attribute)
     * @return a list of unique values, can be empty
     * @throws BadRequestException if the request was invalid, eg. the provided filter could not be
     *     parsed
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed(
            value = "get_unique_attributes",
            description = "time spent to process get unique attributes call")
    public ResponseEntity<Serializable> getUniqueAttributes(
            @Parameter(name = "appId", description = "application id", required = true)
                    @PathVariable("appId")
                    Long appId,
            @Parameter(name = "appLayerId", description = "application layer id", required = true)
                    @PathVariable("appLayerId")
                    Long appLayerId,
            @Parameter(name = "attributeName", description = "attribute name", required = true)
                    @PathVariable("attributeName")
                    String attributeName,
            @RequestParam(required = false) String filter)
            throws BadRequestException {

        // this could throw EntityNotFound, which is handled by #handleEntityNotFoundException
        // and in a normal flow this should not happen
        // as appId is (should be) validated by calling the /app/ endpoint
        final Application application = applicationRepository.getReferenceById(appId);
        if (application.isAuthenticatedRequired() && !AuthUtil.isAuthenticatedUser()) {
            // login required, send RedirectResponse
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new RedirectResponse());
        } else {
            if (StringUtils.isBlank(attributeName)) {
                throw new BadRequestException("Attribute name is required");
            }
            final ApplicationLayer appLayer =
                    applicationLayerRepository.getReferenceById(appLayerId);

            UniqueValuesResponse uniqueValuesResponse =
                    getUniqueValues(appLayer, attributeName, filter);
            return ResponseEntity.status(HttpStatus.OK).body(uniqueValuesResponse);
        }
    }

    private UniqueValuesResponse getUniqueValues(
            ApplicationLayer appLayer, String attributeName, String filter)
            throws BadRequestException {
        final UniqueValuesResponse uniqueValuesResponse =
                new UniqueValuesResponse().filterApplied(false);
        final Layer layer = appLayer.getService().getLayer(appLayer.getLayerName(), entityManager);
        final SimpleFeatureType sft = layer.getFeatureType();
        if (null == sft) {
            return uniqueValuesResponse;
        }

        try {
            Filter existingFilter = null;
            if (null != filter) {
                existingFilter = ECQL.toFilter(filter);
            }
            logger.trace("existingFilter: " + existingFilter);

            Filter notNull = ff.not(ff.isNull(ff.property(attributeName)));
            Filter f = notNull;
            if (null != existingFilter) {
                f = ff.and(notNull, existingFilter);
                uniqueValuesResponse.filterApplied(true);
            }

            Query q = new Query(sft.getTypeName(), f);
            q.setPropertyNames(attributeName);
            q.setSortBy(ff.sort(attributeName, SortOrder.ASCENDING));
            logger.trace("Unique values query: " + q);

            SimpleFeatureSource fs = FeatureSourceFactoryHelper.openGeoToolsFeatureSource(sft);
            // and then there are 2 scenarios:
            // there might be a performance benefit for one or the other
            if (!useGeotoolsUniqueFunction) {
                // #1 use a feature visitor to get the unique values
                // not recommended, as it may not be performant
                logger.trace("Using feature visitor to get unique values");
                fs.getFeatures(q)
                        .accepts(
                                feature ->
                                        uniqueValuesResponse.addValuesItem(
                                                feature.getProperty(attributeName).getValue()),
                                null);
            } else {
                // #2 or use a Function to get the unique values
                // this is the recommended way, uses SLQ "distinct"
                logger.trace("Using geotools unique collection function to get unique values");
                Function unique = ff.function("Collection_Unique", ff.property(attributeName));
                Object o = unique.evaluate(fs.getFeatures(q));
                if (o instanceof Set) {
                    @SuppressWarnings("unchecked")
                    Set<Object> uniqueValues = (Set<Object>) o;
                    uniqueValuesResponse.setValues(uniqueValues);
                }
            }

            fs.getDataStore().dispose();
        } catch (CQLException e) {
            logger.error("Could not parse requested filter.", e);
            throw new BadRequestException("Could not parse requested filter.");
        } catch (IOException e) {
            logger.error("Could not retrieve attribute data.", e);
        }

        return uniqueValuesResponse;
    }
}
