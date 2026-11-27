/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.tailormap.api.persistence.OIDCConfiguration;

class OIDCConfigurationValidatorTest {
  private final OIDCConfigurationValidator validator = new OIDCConfigurationValidator();

  private Errors validate(OIDCConfiguration configuration) {
    Errors errors = new BeanPropertyBindingResult(configuration, "oidcConfiguration");
    validator.validate(configuration, errors);
    return errors;
  }

  @Test
  void accepts_valid_or_no_roles_claim_filter_regex() {
    assertFalse(validate(new OIDCConfiguration().setRolesClaimFilterRegex("^tailormap-"))
        .hasErrors());
    assertFalse(validate(new OIDCConfiguration()).hasErrors());
  }

  @Test
  void rejects_invalid_roles_claim_filter_regex() {
    Errors errors = validate(new OIDCConfiguration().setRolesClaimFilterRegex("[tailormap"));
    assertTrue(errors.hasFieldErrors("rolesClaimFilterRegex"));
    assertEquals(
        "Invalid regular expression for the roles claim filter: Unclosed character class",
        errors.getFieldError("rolesClaimFilterRegex").getDefaultMessage());
  }
}
