/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.exception;

/**
 * General checked {@code Exception} for Tailormap. Prefer to use more specific exceprions.
 *
 * @author mprins
 * @since 0.1
 */
public class TailormapException extends Exception {
    public TailormapException() {
        super();
    }

    public TailormapException(String message) {
        super(message);
    }

    public TailormapException(String message, Throwable cause) {
        super(message, cause);
    }

    public TailormapException(Throwable cause) {
        super(cause);
    }

    protected TailormapException(
            String message,
            Throwable cause,
            boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
