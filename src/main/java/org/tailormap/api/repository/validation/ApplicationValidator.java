/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.repository.validation;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.repository.ApplicationRepository;

@Component
public class ApplicationValidator implements Validator {
  private final ApplicationRepository applicationRepository;

  public ApplicationValidator(ApplicationRepository applicationRepository) {
    this.applicationRepository = applicationRepository;
  }

  @Override
  public boolean supports(@NonNull Class<?> clazz) {
    return Application.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(@NonNull Object target, @NonNull Errors errors) {
    Application app = (Application) target;
    Application existing = applicationRepository.findByName(app.getName());
    if (existing != null && !existing.getId().equals(app.getId())) {
      errors.rejectValue(
          "name",
          "duplicate",
          String.format("Application with name \"%s\" already exists.", app.getName()));
    }
  }
}
