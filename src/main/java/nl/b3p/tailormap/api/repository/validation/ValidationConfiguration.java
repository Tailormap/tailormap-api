/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.repository.validation;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.rest.core.event.ValidatingRepositoryEventListener;
import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurer;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@Configuration
public class ValidationConfiguration implements RepositoryRestConfigurer {
    // JSR-303 validator
    private final LocalValidatorFactoryBean localValidatorFactoryBean;

    private final GeoServiceValidator geoServiceValidator;

    public ValidationConfiguration(LocalValidatorFactoryBean localValidatorFactoryBean, GeoServiceValidator geoServiceValidator) {
        this.localValidatorFactoryBean = localValidatorFactoryBean;
        this.geoServiceValidator = geoServiceValidator;
    }

    @Override
    public void configureValidatingRepositoryEventListener(ValidatingRepositoryEventListener validatingListener) {
        validatingListener.addValidator("beforeCreate", localValidatorFactoryBean);
        validatingListener.addValidator("beforeSave", localValidatorFactoryBean);
        validatingListener.addValidator("beforeCreate", geoServiceValidator);
        validatingListener.addValidator("beforeSave", geoServiceValidator);
    }
}
