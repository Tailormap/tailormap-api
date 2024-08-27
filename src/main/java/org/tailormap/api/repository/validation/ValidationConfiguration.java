/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.repository.validation;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.rest.core.event.ValidatingRepositoryEventListener;
import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurer;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@Configuration
public class ValidationConfiguration implements RepositoryRestConfigurer {
  // JSR-303 validator
  private final LocalValidatorFactoryBean localValidatorFactoryBean;

  private final GeoServiceValidator geoServiceValidator;

  private final FeatureSourceValidator featureSourceValidator;

  private final ApplicationValidator applicationValidator;

  public ValidationConfiguration(
      LocalValidatorFactoryBean localValidatorFactoryBean,
      GeoServiceValidator geoServiceValidator,
      FeatureSourceValidator featureSourceValidator,
      ApplicationValidator applicationValidator) {
    this.localValidatorFactoryBean = localValidatorFactoryBean;
    this.geoServiceValidator = geoServiceValidator;
    this.featureSourceValidator = featureSourceValidator;
    this.applicationValidator = applicationValidator;
  }

  @Override
  public void configureValidatingRepositoryEventListener(
      ValidatingRepositoryEventListener validatingListener) {
    validatingListener.addValidator("beforeCreate", localValidatorFactoryBean);
    validatingListener.addValidator("beforeSave", localValidatorFactoryBean);
    validatingListener.addValidator("beforeCreate", geoServiceValidator);
    validatingListener.addValidator("beforeSave", geoServiceValidator);
    validatingListener.addValidator("beforeCreate", featureSourceValidator);
    validatingListener.addValidator("beforeSave", featureSourceValidator);
    validatingListener.addValidator("beforeCreate", applicationValidator);
    validatingListener.addValidator("beforeSave", applicationValidator);
  }
}
