/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.security;

import java.io.Serializable;

public record TailormapAdditionalProperty(String key, Boolean isPublic, Object value) implements Serializable {}
