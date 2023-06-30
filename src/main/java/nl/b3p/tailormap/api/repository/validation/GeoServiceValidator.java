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
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.helper.GeoServiceHelper;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

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
      uri = new URL(service.getUrl()).toURI();
    } catch (Exception e) {
      errors.rejectValue("url", "invalid", "Invalid URI");
      return;
    }
    if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
      errors.rejectValue("url", "invalid-scheme", "Invalid URI scheme");
      return;
    }

    if (service.isRefreshCapabilities()) {
      try {
        geoServiceHelper.loadServiceCapabilities(service);
      } catch (UnknownHostException e) {
        errors.rejectValue("url", "unknown-host", "Unknown host: \"" + uri.getHost() + "\"");
      } catch (Exception e) {
        String msg =
            String.format(
                "Error loading capabilities from URL \"%s\": %s",
                service.getUrl(), ExceptionUtils.getMessage(e));
        errors.rejectValue("url", "loading-capabilities-failed", msg);
      }
    }
  }
}
