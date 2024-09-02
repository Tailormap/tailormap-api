/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller.admin;

import org.springframework.context.MessageSource;
import org.springframework.data.rest.webmvc.RepositoryRestExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * Also apply the RepositoryRestExceptionHandler from Spring Data REST to our custom controllers so
 * a RepositoryConstraintViolationException gets converted to JSON.
 */
@ControllerAdvice(basePackageClasses = AdminRepositoryRestExceptionHandler.class)
public class AdminRepositoryRestExceptionHandler extends RepositoryRestExceptionHandler {
  public AdminRepositoryRestExceptionHandler(MessageSource messageSource) {
    super(messageSource);
  }
}
