/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.repository.validation;

import static org.tailormap.api.persistence.json.GeoServiceProtocol.XYZ;
import static org.tailormap.api.util.TMExceptionUtils.joinAllThrowableMessages;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.helper.GeoServiceHelper;

@Component
public class GeoServiceValidator implements Validator {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final GeoServiceHelper geoServiceHelper;

  public GeoServiceValidator(GeoServiceHelper geoServiceHelper) {
    this.geoServiceHelper = geoServiceHelper;
  }

  @Override
  public boolean supports(@NonNull Class<?> clazz) {
    return GeoService.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(@NonNull Object target, @NonNull Errors errors) {
    GeoService service = (GeoService) target;
    logger.debug(
        "Validate service {}, refresh capabilities {}, url {}",
        service.getId(),
        service.isRefreshCapabilities(),
        service.getUrl());

    if (errors.getFieldError("url") != null) {
      return;
    }

    URI uri;
    try {
      // For XYZ URL templates, remove replacements
      if (service.getProtocol() == XYZ) {
        // Besides {x}, {y}, {z} also allow {-y} (for TMS) and {a-c} for domains
        uri = new URL(service.getUrl().replaceAll("\\{[a-z\\-]+}", "")).toURI();
      } else {
        uri = new URL(service.getUrl()).toURI();
      }
    } catch (Exception e) {
      errors.rejectValue("url", "invalid", "Invalid URI");
      return;
    }
    if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
      errors.rejectValue("url", "invalid-scheme", "Invalid URI scheme");
      return;
    }

    // For XYZ make sure updated xyzCrs from settings is applied to layer
    if (service.isRefreshCapabilities() || XYZ.equals(service.getProtocol())) {
      try {
        geoServiceHelper.loadServiceCapabilities(service);
      } catch (UnknownHostException e) {
        errors.rejectValue("url", "unknown-host", "Unknown host: \"" + uri.getHost() + "\"");
      } catch (Exception e) {
        String msg =
            String.format(
                "Error loading capabilities from URL \"%s\": %s",
                service.getUrl(), joinAllThrowableMessages(e));
        logger.info(
            "The following exception may not be an application error but could be a problem with an external service or user-entered data: {}",
            msg,
            e);
        errors.rejectValue("url", "loading-capabilities-failed", msg);
      }
    }
  }
}
