/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.repository;

import java.util.UUID;

public record UploadMatch(UUID id, Object hash) {}
