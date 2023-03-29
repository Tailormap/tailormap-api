/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller.admin;

import java.lang.invoke.MethodHandles;
import nl.b3p.tailormap.api.security.InvalidPasswordException;
import nl.b3p.tailormap.api.viewer.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class RestExceptionHandler {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @ExceptionHandler({HttpMessageNotReadableException.class})
  @Nullable
  public ResponseEntity<Object> handleException(
      final HttpMessageNotReadableException ex, final WebRequest request) {
    logger.debug("Invalid message exception", ex);

    // Invalid password was given
    // ideally we would use @ExceptionHandler({InvalidPasswordException.class}) but that does not
    // trigger
    // the exception handler because it is wrapped in HttpMessageNotReadableException
    if (ex.getCause() != null && ex.getMostSpecificCause() instanceof InvalidPasswordException) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              new ErrorResponse()
                  .code(HttpStatus.BAD_REQUEST.value())
                  .message(
                      ((InvalidPasswordException) ex.getMostSpecificCause()).getOriginalMessage()));
    }

    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new ErrorResponse()
                .code(HttpStatus.BAD_REQUEST.value())
                .message(ex.getMostSpecificCause().getLocalizedMessage()));
  }
}
