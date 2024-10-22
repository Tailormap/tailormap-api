/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.repository.validation;

import static org.tailormap.api.util.TMExceptionUtils.joinAllThrowableMessages;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;
import org.tailormap.api.geotools.featuresources.JDBCFeatureSourceHelper;
import org.tailormap.api.geotools.featuresources.WFSFeatureSourceHelper;
import org.tailormap.api.persistence.TMFeatureSource;

@Component
public class FeatureSourceValidator implements Validator {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public boolean supports(@NonNull Class<?> clazz) {
    return TMFeatureSource.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(@NonNull Object target, @NonNull Errors errors) {
    TMFeatureSource featureSource = (TMFeatureSource) target;
    logger.debug("Validate {} feature source {}", featureSource.getProtocol(), featureSource);

    if (featureSource.getProtocol().equals(TMFeatureSource.Protocol.WFS)) {
      validateWFS(featureSource, errors);
    } else if (featureSource.getProtocol().equals(TMFeatureSource.Protocol.JDBC)) {
      validateJDBC(featureSource, errors);
    }
  }

  private void validateWFS(TMFeatureSource featureSource, Errors errors) {
    ValidationUtils.rejectIfEmptyOrWhitespace(errors, "url", "errors.required", "URL is required");

    if (errors.hasErrors()) {
      return;
    }

    URI uri;
    try {
      uri = new URL(featureSource.getUrl()).toURI();
    } catch (Exception e) {
      errors.rejectValue("url", "errors.url.invalid", "Invalid URI");
      return;
    }
    if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
      errors.rejectValue("url", "errors.url.invalid-scheme", "Invalid URI scheme");
      return;
    }

    if (featureSource.isRefreshCapabilities()) {
      try {
        new WFSFeatureSourceHelper().loadCapabilities(featureSource);
      } catch (UnknownHostException e) {
        errors.rejectValue("url", "errors.unknown-host", "Unknown host: \"" + uri.getHost() + "\"");
      } catch (Exception e) {
        String msg =
            String.format(
                "Error loading WFS capabilities from URL \"%s\": %s",
                featureSource.getUrl(), joinAllThrowableMessages(e));
        logger.info(
            "The following exception may not be an application error but could be a problem with an external service or user-entered data: {}",
            msg,
            e);
        errors.rejectValue("url", "errors.loading-capabilities-failed", msg);
      }
    }
  }

  private void validateJDBC(TMFeatureSource featureSource, Errors errors) {
    if (featureSource.getJdbcConnection() == null) {
      errors.rejectValue(
          "jdbcConnection", "errors.required", "JDBC connection properties are required");
      return;
    }
    if (featureSource.getAuthentication() == null) {
      errors.rejectValue(
          "authentication", "errors.required", "Database username and password are required");
      return;
    }

    if (errors.hasErrors()) {
      return;
    }

    if (featureSource.isRefreshCapabilities()) {
      try {
        new JDBCFeatureSourceHelper().loadCapabilities(featureSource);
      } catch (Exception e) {
        String msg =
            String.format(
                "Error loading capabilities from JDBC datastore: %s",
                ExceptionUtils.getRootCauseMessage(e));
        logger.info(
            "The following exception may not be an application error but could be a problem with an external service or user-entered data: {}",
            msg,
            e);
        errors.rejectValue("url", "errors.loading-capabilities-failed", msg);
      }
    }
  }
}
