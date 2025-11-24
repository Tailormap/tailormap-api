/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.security;

import java.io.Closeable;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.databind.DatabindException;

public class InvalidPasswordException extends DatabindException {

  public InvalidPasswordException(Closeable processor) {
    super(processor, "Invalid password.");
  }

  public InvalidPasswordException(Closeable processor, String msg) {
    super(processor, msg);
  }

  public InvalidPasswordException(Closeable processor, String msg, Throwable problem) {
    super(processor, msg, problem);
  }

  public InvalidPasswordException(Closeable processor, String msg, TokenStreamLocation loc) {
    super(processor, msg, loc);
  }
}
