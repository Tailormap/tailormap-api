/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import io.micrometer.core.annotation.Timed;

import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.geotools.featuresources.FeatureSourceFactoryHelper;
import nl.b3p.tailormap.api.model.UniqueValuesResponse;
import nl.b3p.tailormap.api.repository.LayerRepository;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.Serializable;
import java.util.Set;
import java.util.stream.Collectors;

@AppRestController
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
    private final LayerRepository layerRepository;

    @Autowired
    public UniqueValuesController(LayerRepository layerRepository) {
        this.layerRepository = layerRepository;
    }

    /**
     * Get a list of unique attribute values for a given attribute name.
     *
     * @param application the application
     * @param applicationLayer the application layer id
     * @param attributeName the attribute name
     * @param filter A filter that was already applied to the layer (on a different attribute or
     *     this attribute)
     * @return a list of unique values, can be empty parsed
     */
    @RequestMapping(method = {GET, POST})
    @Timed(
            value = "get_unique_attributes",
            description = "time spent to process get unique attributes call")
    public ResponseEntity<Serializable> getUniqueAttributes(
            @ModelAttribute Application application,
            @ModelAttribute ApplicationLayer applicationLayer,
            @PathVariable("attributeName") String attributeName,
            @RequestParam(required = false) String filter) {
        if (StringUtils.isBlank(attributeName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attribute name is required");
        }

        UniqueValuesResponse uniqueValuesResponse =
                getUniqueValues(applicationLayer, attributeName, filter);
        return ResponseEntity.status(HttpStatus.OK).body(uniqueValuesResponse);
    }

    private UniqueValuesResponse getUniqueValues(
            ApplicationLayer appLayer, String attributeName, String filter) {
        final UniqueValuesResponse uniqueValuesResponse =
                new UniqueValuesResponse().filterApplied(false);
        final Layer layer =
                layerRepository.getByServiceAndName(appLayer.getService(), appLayer.getLayerName());
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
                // this is the recommended way, uses SQL "distinct"
                logger.trace("Using geotools unique collection function to get unique values");
                Function unique = ff.function("Collection_Unique", ff.property(attributeName));
                Object o = unique.evaluate(fs.getFeatures(q));
                if (o instanceof Set) {
                    @SuppressWarnings("unchecked")
                    Set<Object> uniqueValues = (Set<Object>) o;
                    uniqueValuesResponse.setValues(
                            uniqueValues.stream().sorted().collect(Collectors.toList()));
                }
            }

            fs.getDataStore().dispose();
        } catch (CQLException e) {
            logger.error("Could not parse requested filter.", e);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Could not parse requested filter.");
        } catch (IOException e) {
            logger.error("Could not retrieve attribute data.", e);
        }

        return uniqueValuesResponse;
    }
}
