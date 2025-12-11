/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.security;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Mixin to allow polymorphic deserialization of TailormapUserDetailsImpl when restoring SecurityContext. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@class")
public abstract class TailormapUserDetailsMixin {}
