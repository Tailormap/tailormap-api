/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.repository.validation;

import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

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
