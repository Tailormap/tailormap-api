/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.repository.validation;

import nl.b3p.tailormap.api.persistence.GeoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URL;

@Component
public class GeoServiceValidator implements Validator {
    private static final Logger logger =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Override
    public boolean supports(@NonNull Class<?> clazz) {
        return GeoService.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(@NonNull Object target, @NonNull Errors errors) {
        GeoService service = (GeoService) target;
        logger.debug("Validate service {}", service.getId());

        URI uri;
        try {
            uri = new URL(service.getUrl()).toURI();
        } catch (Exception e) {
            errors.rejectValue("url", "invalid", "Invalid URI");
            return;
        }
        if(!"https".equals(uri.getScheme())) {
            errors.rejectValue("url", "invalid-scheme", "Invalid URI scheme");
        }
        // Do not check DNS name resolution; in case we support internet access using a proxy in the future


    }
}
