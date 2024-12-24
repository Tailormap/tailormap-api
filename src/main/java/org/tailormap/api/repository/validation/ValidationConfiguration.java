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

  private final SearchIndexValidator searchIndexValidator;

  public ValidationConfiguration(
      LocalValidatorFactoryBean localValidatorFactoryBean,
      GeoServiceValidator geoServiceValidator,
      FeatureSourceValidator featureSourceValidator,
      ApplicationValidator applicationValidator,
      SearchIndexValidator searchIndexValidator) {
    this.localValidatorFactoryBean = localValidatorFactoryBean;
    this.geoServiceValidator = geoServiceValidator;
    this.featureSourceValidator = featureSourceValidator;
    this.applicationValidator = applicationValidator;
    this.searchIndexValidator = searchIndexValidator;
  }

  @Override
  public void configureValidatingRepositoryEventListener(ValidatingRepositoryEventListener validatingListener) {
    validatingListener
        .addValidator("beforeCreate", localValidatorFactoryBean)
        .addValidator("beforeSave", localValidatorFactoryBean)
        .addValidator("beforeCreate", geoServiceValidator)
        .addValidator("beforeSave", geoServiceValidator)
        .addValidator("beforeCreate", featureSourceValidator)
        .addValidator("beforeSave", featureSourceValidator)
        .addValidator("beforeCreate", applicationValidator)
        .addValidator("beforeSave", applicationValidator)
        .addValidator("beforeCreate", searchIndexValidator)
        .addValidator("beforeSave", searchIndexValidator);
  }
}
