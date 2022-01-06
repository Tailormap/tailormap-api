/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.exception;

/**
 * A checked {@code Exception} to indicate a configuration problem in Tailormap.
 *
 * @author mprins
 * @since 0.1
 */
public class TailormapConfigurationException extends TailormapException {
    public TailormapConfigurationException() {
        super();
    }

    public TailormapConfigurationException(String message) {
        super(message);
    }

    public TailormapConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public TailormapConfigurationException(Throwable cause) {
        super(cause);
    }

    protected TailormapConfigurationException(
            String message,
            Throwable cause,
            boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
