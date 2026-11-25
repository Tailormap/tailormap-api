/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository.validation;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.tailormap.api.persistence.OIDCConfiguration;

@Component
public class OIDCConfigurationValidator implements Validator {
  @Override
  public boolean supports(@NonNull Class<?> clazz) {
    return OIDCConfiguration.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(@NonNull Object target, @NonNull Errors errors) {
    OIDCConfiguration configuration = (OIDCConfiguration) target;
    String regex = configuration.getRolesClaimFilterRegex();
    if (regex != null) {
      try {
        Pattern.compile(regex);
      } catch (PatternSyntaxException e) {
        errors.rejectValue(
            "rolesClaimFilterRegex",
            "invalid",
            "Invalid regular expression for the roles claim filter: %s".formatted(e.getDescription()));
      }
    }
  }
}
