/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository.validation;

import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.tailormap.api.persistence.SearchIndex;
import org.tailormap.api.persistence.TMFeatureSource;
import org.tailormap.api.repository.FeatureTypeRepository;

@Component
public class SearchIndexValidator implements Validator {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final FeatureTypeRepository featureTypeRepository;

  public SearchIndexValidator(FeatureTypeRepository featureTypeRepository) {
    this.featureTypeRepository = featureTypeRepository;
  }

  @Override
  public boolean supports(@NonNull Class<?> clazz) {
    return SearchIndex.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(@NonNull Object target, @NonNull Errors errors) {
    final SearchIndex searchIndex = (SearchIndex) target;
    if (searchIndex.getFeatureTypeId() != null) {
      featureTypeRepository.findById(searchIndex.getFeatureTypeId()).ifPresent((ft) -> {
        if (TMFeatureSource.Protocol.WFS.equals(ft.getFeatureSource().getProtocol())) {
          logger.warn(
              "Attempt to index feature type '{}' from unsupported WFS source '{}'.",
              ft.getName(),
              ft.getFeatureSource().getTitle());
          errors.rejectValue("featureTypeId", "invalid", "This feature type is not available for indexing.");
        }
      });
    }
  }
}
