/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.util;

import java.io.Serializable;
import nl.b3p.tailormap.api.viewer.model.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class ErrorResponseBuilder {

  public static ResponseEntity<Serializable> notFound() {
    return build(HttpStatus.NOT_FOUND);
  }

  public static ResponseEntity<Serializable> notFound(String message) {
    return build(HttpStatus.NOT_FOUND, message);
  }

  public static ResponseEntity<Serializable> badRequest() {
    return build(HttpStatus.BAD_REQUEST);
  }

  public static ResponseEntity<Serializable> internalServerError() {
    return build(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  public static ResponseEntity<Serializable> forbidden() {
    return build(HttpStatus.FORBIDDEN);
  }

  public static ResponseEntity<Serializable> build(HttpStatus status) {
    return ResponseEntity.status(status)
        .body(new ErrorResponse().code(status.value()).message(status.getReasonPhrase()));
  }

  public static ResponseEntity<Serializable> build(HttpStatus status, String message) {
    return ResponseEntity.status(status)
        .body(new ErrorResponse().code(status.value()).message(message));
  }
}
