/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.security;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.databind.JsonMappingException;
import java.io.Closeable;

public class InvalidPasswordException extends JsonMappingException {

  public InvalidPasswordException(Closeable processor) {
    super(processor, "Invalid password.");
  }

  public InvalidPasswordException(Closeable processor, String msg) {
    super(processor, msg);
  }

  public InvalidPasswordException(Closeable processor, String msg, Throwable problem) {
    super(processor, msg, problem);
  }

  public InvalidPasswordException(Closeable processor, String msg, JsonLocation loc) {
    super(processor, msg, loc);
  }
}
