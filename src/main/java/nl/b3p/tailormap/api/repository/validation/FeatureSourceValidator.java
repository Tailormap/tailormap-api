/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.repository.validation;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import nl.b3p.tailormap.api.geotools.featuresources.JDBCFeatureSourceHelper;
import nl.b3p.tailormap.api.geotools.featuresources.WFSFeatureSourceHelper;
import nl.b3p.tailormap.api.persistence.TMFeatureSource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

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
    if (errors.getFieldError("url") != null) {
      return;
    }

    URI uri;
    try {
      uri = new URL(featureSource.getUrl()).toURI();
    } catch (Exception e) {
      errors.rejectValue("url", "invalid", "Invalid URI");
      return;
    }
    if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
      errors.rejectValue("url", "invalid-scheme", "Invalid URI scheme");
      return;
    }

    if (featureSource.isRefreshCapabilities()) {
      try {
        new WFSFeatureSourceHelper().loadCapabilities(featureSource);
      } catch (UnknownHostException e) {
        errors.rejectValue("url", "unknown-host", "Unknown host: \"" + uri.getHost() + "\"");
      } catch (Exception e) {
        String msg =
            String.format(
                "Error loading WFS capabilities from URL \"%s\": %s",
                featureSource.getUrl(), ExceptionUtils.getMessage(e));
        String loggerMsg =
            " -- This may not be an application error but a problem with an external service or user-entered data.";
        logger.info(msg + loggerMsg, e);
        errors.rejectValue("url", "loading-capabilities-failed", msg);
      }
    }
  }

  private void validateJDBC(TMFeatureSource featureSource, Errors errors) {
    if (featureSource.isRefreshCapabilities()) {
      try {
        new JDBCFeatureSourceHelper().loadCapabilities(featureSource);
      } catch (Exception e) {
        String msg =
            String.format(
                "Error loading capabilities from JDBC datastore \"%s\": %s",
                featureSource.getJdbcConnection(), ExceptionUtils.getMessage(e));
        String loggerMsg =
            " -- This may not be an application error but a problem with an external service or user-entered data.";
        logger.info(msg + loggerMsg, e);
        errors.rejectValue("url", "loading-capabilities-failed", msg);
      }
    }
  }
}
