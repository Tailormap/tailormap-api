/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.security;

import java.io.Serializable;

public record TailormapAdditionalProperty(
    String key,
    Boolean isPublic,
    //    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
    Object value)
    implements Serializable {}
